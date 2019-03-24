build:
	./gradlew build

upload: build
	./gradlew uploadArchives
