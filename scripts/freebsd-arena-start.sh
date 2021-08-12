cp SQLite-ABI-FreeBSD-amd64/libsqlitejdbc.so /usr/local/lib/.
cd ..
git pull
mvn clean install -Dmaven.test.skip=true -P run
cd Z-Arena || exit
nohup mvn spring-boot:run -Dspring-boot.run.profiles=daily "-Dspring-boot.run.jvmArguments=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000" &
tail -f ~/Z-Chess/logs/z-chess.log