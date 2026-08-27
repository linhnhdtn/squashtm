# squash-tm-dtn

Bản Squash TM 13.0.7 kèm phần custom của DTN: plugin Java, một trang dashboard riêng, việt hoá nhãn UI, và (tuỳ chọn) một trang nằm hẳn trong SPA Angular.

Repo **chỉ chứa source + script**. Bộ cài Squash TM (342 MB) và frontend Angular được tải/ build lúc `bootstrap`, không commit vào git.

---

## Yêu cầu

| | |
|---|---|
| **JDK 21** | bắt buộc — `startup.sh` của Squash chặn Java thấp hơn. Khai đường dẫn trong `.env` (`JAVA_HOME`) |
| **Docker** + compose plugin | chạy PostgreSQL |
| `curl`, `unzip`, `zip`, `tar` | bootstrap và patch war |
| *(chỉ khi build frontend)* **Node 22 + yarn 1.x** | `yarn install` ~1.1 GB, build ~5 phút |

Squash TM 13 **không còn hỗ trợ H2** — validator chỉ nhận `jdbc:postgresql://` hoặc `jdbc:mariadb://`.

---

## Chạy lần đầu

```bash
git clone git@github.com:linhnhdtn/squashtm.git && cd squashtm
cp .env.example .env && $EDITOR .env      # sửa JAVA_HOME, port, mật khẩu DB
make bootstrap                            # tải Squash + DB + schema + build plugin
make dev                                  # khởi động
```

Xong sẽ in ra URL. Mặc định: <http://localhost:8080/squash/> — tài khoản `admin` / `admin` (**đổi ngay** trong *My account → Local password*).

Muốn kèm trang trong SPA Angular:

```bash
make bootstrap-full        # = bootstrap + clone tag v13.0.7 + apply patch + yarn build (~10 phút)
```

---

## Lệnh hằng ngày

| Lệnh | Việc |
|---|---|
| `make dev` / `stop` / `restart` / `status` / `logs` | quản lý app (chạy `setsid`, không chết khi đóng terminal) |
| `make plugin` | build lại plugin Java + deploy + restart (~10 giây) |
| `make front` | build lại frontend fork + nhồi vào war + restart (~5 phút) |
| `make clean` | xoá artifact, trả `squash-tm.war` về bản gốc (giữ DB) |
| `make clean-all` | xoá `.runtime/` + `.cache/` (**giữ** volume DB) |

`bun run dev`, `bun run plugin`… cũng dùng được — `package.json` chỉ là vỏ bọc gọi lại các script này.

---

## Cấu trúc

```
plugin/                     Plugin Java (6 class) + trang HTML/JS thuần + i18n
  build.sh                  javac + jar, không cần Maven/mạng
front-patch/                Phần Angular (chỉ patch, không commit jar build)
  tm-front-v13.0.7.patch    4 file, 55 dòng thêm, 0 dòng xoá
  dtn-dashboard/            page mới (module + component)
  apply.sh                  clone tag + apply patch + yarn build
  repack-front.sh           nhồi dist vào war  (--restore để rollback)
ops/
  squashtm.sh               start/stop/status/logs
  patch-branding.sh         chèn custom.js vào index.html của SPA
  docker-compose.yml        Postgres, có named volume, bind 127.0.0.1
  squash-tm.service         systemd user unit (tự chạy khi bật máy)
conf/lang/                  việt hoá nhãn UI (custom_translations_*.json)
.runtime/  .cache/          sinh ra lúc bootstrap — đã gitignore
```

## Hai trang, đừng nhầm

| | Trang plugin | Trang trong SPA |
|---|---|---|
| URL | `/squash/plugin/dtn-myfeature/index` | `/squash/dtn-dashboard` |
| Code | `plugin/` (Java + HTML/JS thuần) | `front-patch/` (Angular) |
| Cần build frontend? | không | **có** (`make front`) |
| Điều hướng | reload trang | client-side, nav bar thật của `sqtm-core` |
| Upgrade Squash | không phải làm gì | apply lại 5 chỗ trong patch |

Chưa chạy `make front` thì `/squash/dtn-dashboard` trả về vỏ SPA nhưng Angular không có route đó → trang trắng. Dùng trang plugin.

---

## Những bẫy đã trả giá để biết (đừng sửa lại thành sai)

| Bẫy | Hệ quả nếu làm sai |
|---|---|
| `javac -parameters` | thiếu → `@PathVariable` lỗi runtime `Name for argument of type [long] not specified` → HTTP 500 |
| Nested jar trong war phải **Stored** (`zip -0`) | nén lại → Spring Boot loader không đọc được → app 500 `FileNotFoundException: META-INF/resources/index.html` |
| `MenuItem.getAccessRule()` phải là node-selection rule | `AccessRuleBuilder.anybody()` → Jackson vỡ `/backend/referential`; `null` → menu xám vĩnh viễn |
| Wizard chỉ hiện ở `CAMPAIGN_WORKSPACE` / `REQUIREMENT_WORKSPACE` | khai `TEST_CASE_WORKSPACE` → đăng ký thành công nhưng không màn hình nào render |
| `/plugin/**` **không** được Squash auth | endpoint plugin phải tự `@PreAuthorize`, nếu không là public |
| Route SPA phải khai ở Java (`AngularAppPageUrls` của core) | thiếu → gõ URL/F5 bị **404**; đã xử lý bằng `DtnMyFeatureWebMvcConfig` + `DtnSpaShellSecurityExemption` trong plugin |
| `squash.db.update-mode` | chỉ nhận `interactive / only / forced / disabled` — **không có** `auto` |
| Sửa `index.html`/`custom.js`/i18n mà không thấy đổi | static bị cache 7 ngày → để `STATIC_CACHE=0` khi dev, hoặc Ctrl+Shift+R |

---

## Upgrade sang bản Squash mới

1. Sửa `SQUASH_VERSION` trong `.env`, chạy lại `make bootstrap` (schema sẽ được liquibase cập nhật với `update-mode=forced`).
2. `make plugin` — thường không cần sửa gì.
3. Nếu dùng frontend fork: đổi tên tag trong `front-patch/`, `git apply` lại patch (5 chỗ: `app.module.ts`, `pages/dtn-dashboard/`, `nav-bar.component.html`, `themes.ts`, `theme.model.ts`), rồi `make front`.

## Lưu ý bản quyền

Bộ cài của Henix có thư mục `plugin-files/` chứa các plugin **Premium/Ultimate** (Jira, Azure DevOps, SAML, Campaign Assistant…) — **không** commit chúng vào repo này. Core Squash TM là LGPL v3; plugin `automation.scm.git` của Henix là proprietary.
