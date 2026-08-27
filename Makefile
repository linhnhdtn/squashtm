.PHONY: help bootstrap bootstrap-full dev stop restart status logs plugin front clean clean-all

help:
	@echo "make bootstrap       # dung tu dau: tai Squash + DB + schema + plugin Java"
	@echo "make bootstrap-full  # nhu tren, kem fork frontend Angular (+10 phut)"
	@echo "make dev             # start (mo tren LAN theo BIND_ADDRESS trong .env)"
	@echo "make stop | restart | status | logs"
	@echo "make plugin          # build lai plugin Java + deploy (~10s)"
	@echo "make front           # build lai frontend fork + nhoi vao war (~5 phut)"
	@echo "make clean           # xoa war da patch va build artifact (giu DB + bo cai)"
	@echo "make clean-all       # xoa het .runtime/.cache (GIU volume DB)"

bootstrap:      ; ./bootstrap.sh
bootstrap-full: ; ./bootstrap.sh --with-front
dev:            ; ./ops/squashtm.sh start
stop:           ; ./ops/squashtm.sh stop
restart:        ; ./ops/squashtm.sh restart
status:         ; ./ops/squashtm.sh status
logs:           ; ./ops/squashtm.sh logs

plugin:
	./plugin/build.sh --deploy
	./ops/squashtm.sh restart

front:
	./front-patch/apply.sh
	./front-patch/repack-front.sh
	./ops/patch-branding.sh
	./ops/squashtm.sh restart

clean:
	rm -rf plugin/target
	-./front-patch/repack-front.sh --restore

clean-all: stop
	rm -rf .runtime .cache
