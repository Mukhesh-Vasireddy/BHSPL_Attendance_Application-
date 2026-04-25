@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup batch script
@REM ----------------------------------------------------------------------------
@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET DP0=%~dp0
@SET MAVEN_WRAPPER_JAR="%DP0%.mvn\wrapper\maven-wrapper.jar"
@SET MAVEN_OPTS_SAVE=%MAVEN_OPTS%

@IF EXIST %MAVEN_WRAPPER_JAR% GOTO foundWrapper

@SET DOWNLOAD_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
@echo Downloading: %DOWNLOAD_URL%
@powershell -Command "Invoke-WebRequest -Uri '%DOWNLOAD_URL%' -OutFile '%MAVEN_WRAPPER_JAR%'" || GOTO end

:foundWrapper
@SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain
@SET MAVEN_PROJECTBASEDIR=%DP0%

@FOR /F "tokens=1* delims==" %%A IN ('type .mvn\wrapper\maven-wrapper.properties') DO (
    IF "%%A"=="distributionUrl" SET DISTRIBUTION_URL=%%B
)

@SET JAVA_TOOL_OPTIONS=
@SET MVNW_REPODIR=%USERPROFILE%\.m2\wrapper
@IF NOT EXIST "%MVNW_REPODIR%" mkdir "%MVNW_REPODIR%"

@REM Derive the Maven home from distributionUrl
@FOR /F "tokens=*" %%i IN ('powershell -NoProfile -Command "(Split-Path -Leaf \"%DISTRIBUTION_URL%\").Replace(\"-bin.zip\",\"\")"') DO SET MAVEN_DIST_NAME=%%i
@SET M2_HOME=%MVNW_REPODIR%\%MAVEN_DIST_NAME%
@SET MAVEN_ZIP=%MVNW_REPODIR%\%MAVEN_DIST_NAME%-bin.zip

@IF EXIST "%M2_HOME%\bin\mvn.cmd" GOTO runMaven

@echo Downloading Maven %MAVEN_DIST_NAME%...
@powershell -NoProfile -Command "Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%MAVEN_ZIP%'" || (echo Download failed & GOTO end)
@echo Extracting...
@powershell -NoProfile -Command "Expand-Archive -LiteralPath '%MAVEN_ZIP%' -DestinationPath '%MVNW_REPODIR%' -Force"
@del "%MAVEN_ZIP%"

:runMaven
@SET PATH=%M2_HOME%\bin;%PATH%
@SET MAVEN_OPTS=%MAVEN_OPTS_SAVE%
@call mvn.cmd %*

:end
@SET MAVEN_OPTS=%MAVEN_OPTS_SAVE%
