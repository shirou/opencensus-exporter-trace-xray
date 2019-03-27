.PHONY: build upload

build:
	./gradlew build

upload: build
	./gradlew uploadArchives
