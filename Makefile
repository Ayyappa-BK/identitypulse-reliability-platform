.PHONY: test verify lab load failures clean
test:
	mvn -B test
verify:
	mvn -B verify
lab:
	sh scripts/lab-up.sh
load:
	TOKEN="$$(python3 scripts/lab-token.py)" python3 scripts/load.py
failures:
	@for scenario in crash database redis latency; do TOKEN="$$(python3 scripts/lab-token.py)" python3 scripts/failure-test.py $$scenario || exit 1; done
clean:
	docker compose down -v
