@echo off
echo 设置脚本执行权限...

REM 在Windows下，.sh文件需要通过Git Bash或WSL执行
REM 这里创建对应的.bat文件来在Windows下执行

echo 创建Windows批处理脚本...

REM 创建部署脚本的Windows版本
echo @echo off > deploy.bat
echo echo 正在部署API网关... >> deploy.bat
echo docker-compose up -d >> deploy.bat
echo echo 部署完成！ >> deploy.bat

REM 创建管理脚本的Windows版本
echo @echo off > manage.bat
echo set KONG_ADMIN_URL=http://localhost:8001 >> manage.bat
echo set NGINX_URL=http://localhost >> manage.bat
echo. >> manage.bat
echo if "%%1"=="status" ( >> manage.bat
echo     curl -s %%KONG_ADMIN_URL%%/status >> manage.bat
echo ) else if "%%1"=="health" ( >> manage.bat
echo     curl -s %%NGINX_URL%%/health >> manage.bat
echo ) else if "%%1"=="logs" ( >> manage.bat
echo     docker-compose logs -f %%2 >> manage.bat
echo ) else ( >> manage.bat
echo     echo 用法: manage.bat {status^|health^|logs} >> manage.bat
echo ) >> manage.bat

echo Windows批处理脚本创建完成！
echo.
echo 使用方法:
echo   部署: deploy.bat
echo   管理: manage.bat status
echo   日志: manage.bat logs nginx
echo.
pause