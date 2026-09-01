@echo off
setlocal
cd /d %~dp0

if not exist .venv\Scripts\python.exe (
  echo [1/3] Creating Python virtual environment...
  python -m venv .venv
  if errorlevel 1 goto :error
)

call .venv\Scripts\activate.bat

echo [2/3] Installing/updating dependencies...
python -m pip install -q -r requirements.txt
if errorlevel 1 goto :error

if "%GAS_STATION_API_KEY%"=="" set "GAS_STATION_API_KEY=gas-station-local"

echo [3/3] Starting gas station server...
echo API key: %GAS_STATION_API_KEY%
echo Open http://127.0.0.1:8000/health on this PC to test.
echo Press Ctrl+C to stop.
python -m uvicorn main:app --host 0.0.0.0 --port 8000
exit /b 0

:error
echo.
echo Startup failed. Check that Python 3 is installed and available as "python".
pause
exit /b 1
