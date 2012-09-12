java -Dfile.encoding=UTF8 -Dsbt.log.noformat=true -Xmx2048M -Xss4M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=1024m -jar project/sbt-launch-0.12.0.jar "$@"
