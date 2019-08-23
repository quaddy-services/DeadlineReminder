echo on

set t=%temp%\build-dir-deadline-reminder

set actualDir=%CD%

rmdir %t%\target /s /q
mkdir %t%\target

if x%java_home%x == xx set java_home=c:\programs\currentJDK
%java_home%\bin\java -version 2>src\main\resources\make-info.txt
type src\main\resources\make-info.txt
if %errorlevel% == 1 goto noJDK

echo %date% >src\main\resources\version.txt

robocopy src %t%\src *.* /mir 
copy pom.xml %t% /y

cd /D %t%

call mvn -U clean install source:jar
if %errorlevel% == 1 goto ende
echo on

del %actualDir%\target\*.jar
copy target\*.jar %actualDir%\target\ /y

goto ende

:noJDK
echo off
echo ERROR:
echo there is no link to the newest JDK in c:\programs\currentJDK
echo you can create it via c:\Programs\SysinternalsSuite\junction.exe c:\programs\currentjdk "%currentjdk%"
echo see https://technet.microsoft.com/de-de/sysinternals
goto ende

:ende

cd /D %actualDir%
