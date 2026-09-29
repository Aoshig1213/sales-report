@echo off
rem 売上集計ツール: input フォルダの CSV / Excel を集計して output フォルダに帳票を出力する
cd /d "%~dp0"

where java >nul 2>&1
if errorlevel 1 (
  echo Java が見つかりません。Java 21 以降をインストールしてから、もう一度実行してください。
  echo   https://adoptium.net/ja/temurin/releases/
  echo.
  pause
  exit /b 1
)

java -jar sales-report.jar input output
if errorlevel 1 (
  echo.
  echo 集計できませんでした。上のメッセージを確認してください。
) else (
  start "" "%~dp0output"
)
echo.
pause
