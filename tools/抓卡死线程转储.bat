@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

rem ============================================================
rem  GTNEcore 卡死取证：抓取 Minecraft 客户端的 Java 线程转储
rem  用法：游戏卡死时（CPU 吃满、窗口无响应），双击本文件即可。
rem  产物：项目根目录下 dump-<时间戳>.txt —— 里面能看到"哪个 mod 的哪个线程
rem        正在改 NBT / 卡在什么锁上"，是定位退出存档卡死的关键证据。
rem ============================================================

cd /d "%~dp0"

set "JPS="
set "JSTACK="
if exist "%USERPROFILE%\.jdks\ms-17.0.20\bin\jps.exe" (
    set "JPS=%USERPROFILE%\.jdks\ms-17.0.20\bin\jps.exe"
    set "JSTACK=%USERPROFILE%\.jdks\ms-17.0.20\bin\jstack.exe"
)
if not defined JPS (
    for /f "delims=" %%i in ('where jps 2^>nul') do set "JPS=%%i"
)
if not defined JPS (
    for /f "delims=" %%i in ('where jstack 2^>nul') do set "JSTACK=%%i"
    if defined JSTACK (
        for %%d in ("%JSTACK%") do set "JPS=%%~dpdjps.exe"
    )
)
if not defined JPS (
    echo [错误] 找不到 jps/jstack。请手动执行：
    echo     "%USERPROFILE%\.jdks\ms-17.0.20\bin\jps.exe" -l
    echo     "%USERPROFILE%\.jdks\ms-17.0.20\bin\jstack.exe" ^<PID^> ^> dump.txt
    pause
    exit /b 1
)

echo 正在查找 Minecraft 客户端进程 ...
set "PID="
for /f "tokens=1,2" %%a in ('"%JPS%" -l 2^>nul') do (
    echo    %%a  %%b | findstr /i "forgeclientuserdev bootstraplauncher" >nul && (
        set "PID=%%a"
        echo    -^> 命中客户端 PID=%%a
    )
)

if not defined PID (
    echo.
    echo [未找到客户端进程] 可能游戏已经退出/被强杀，或还没启动。
    echo 当前 Java 进程列表：
    "%JPS%" -l
    pause
    exit /b 1
)

for /f "tokens=2 delims==" %%t in ('wmic os get localdatetime /value 2^>nul ^| findstr "="') do set "TS=%%t"
set "TS=%TS:~0,8%-%TS:~8,6%"
set "OUT=dump-%TS%.txt"

echo.
echo 正在抓取线程转储 -^> %OUT%
"%JSTACK%" -l %PID% > "%OUT%" 2>&1
if errorlevel 1 (
    echo [jstack 失败] 尝试不带 -l 再抓一次 ...
    "%JSTACK%" %PID% > "%OUT%" 2>&1
)

echo.
echo ==== 完成：%OUT% ====
echo 快速预览（IO-Worker / 服务器线程 / 世界保存相关）：
findstr /i /n "IO-Worker Server thread Saving ChunkStorage CompoundTag NbtIo HashMap" "%OUT%" | more +0
echo.
echo 请把 %OUT% 交给 AI 分析。
pause
