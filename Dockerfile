# 1단계: 자바 기반 이미지를 사용합니다.
# 공식 자바 17 JDK 이미지(경량화된 버전)를 베이스로 사용합니다.
FROM eclipse-temurin:17-jre-focal

# 2단계: 컨테이너 내부에서 사용할 작업 디렉토리를 설정합니다.
WORKDIR /app

# 3단계: 로컬에서 빌드한 Jar 파일을 컨테이너로 복사합니다.
# (프로젝트를 `gradlew bootJar` 명령어로 빌드하면 `build/libs/`에 Jar 파일이 생성됩니다.)
COPY build/libs/*.jar app.jar

# 4단계: 애플리케이션을 실행할 명령어를 정의합니다.
# `java -jar app.jar` 명령어로 스프링 부트 애플리케이션을 실행합니다.
CMD ["java", "-jar", "app.jar"]