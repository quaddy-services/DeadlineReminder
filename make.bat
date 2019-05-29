echo on
set java_home=c:\programs\currentJDK
%java_home%\bin\java -version
if %errorlevel% == 1 goto noJDK

echo %date% >src/main/resources/version.txt
del target\DeadlineReminder-SNAPSHOT.zip
del target\DeadlineReminder-SNAPSHOT.jar
call mvn -U clean install source:jar
rem  webstart:jnlp
if %errorlevel% == 1 goto ende
if not exist target\DeadlineReminder-SNAPSHOT.zip goto ende
rem if not exist target\DeadlineReminder-SNAPSHOT.jar goto ende
echo on


rem copy target\DeadlineReminder-SNAPSHOT.jar deploy\DeadlineReminder.jar /y
rem cd deploy
rem "%JAVA_HOME%\bin\jarsigner" -keystore myKeystore -storepass quaddy DeadlineReminder.jar Quaddy-Services.de

if not exist Q:\. goto noq
rem pause
pushd .
rem copy DeadlineReminder*.* Q:\HTML\server.src\quaddy-services\deadline-reminder /y
copy target\jnlp\*.* Q:\HTML\server.src\quaddy-services\deadline-reminder /y /d
copy target\jnlp\*.* Q:\HTML\server\quaddy-services\deadline-reminder /y /d
copy src\version.txt Q:\HTML\server.src\quaddy-services\deadline-reminder /y
copy src\versionhistory.htm Q:\HTML\server.src\quaddy-services\deadline-reminder\vhistory.htm /y
cd /d q:\java\adr.de
rem java -cp .;bin de.adr.html.DynamicHtml
java -Djava.net.preferIPv4Stack=true -cp .;bin;lib\commons-net-1.4.1.jar;lib\jakarta-oro-2.0.8.jar de.adr.ftp.FtpUpdate
popd .
:noq
rem call mvn clean

goto ende

:noJDK
echo off
echo ERROR:
echo there is no link to the newest JDK in c:\programs\currentJDK
echo you can create it via c:\Programs\SysinternalsSuite\junction.exe c:\programs\currentjdk "%currentjdk%"
echo see https://technet.microsoft.com/de-de/sysinternals
goto ende

:ende