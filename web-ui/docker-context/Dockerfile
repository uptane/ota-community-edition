FROM nginx:latest as ota-nginx

# WORKDIR /
 
 COPY './confs/nginx.conf' '/etc/nginx/conf.d/app.template'
#  COPY './confs/default.conf' '/etc/nginx/conf.d/app.template'
 COPY './dist' '/usr/app'
#  COPY './dist/spa' '/usr/share/nginx/html'
 COPY './proxy.txt' '/proxy.txt'
 COPY './start.sh' '/start.sh'

EXPOSE 80

ENTRYPOINT ["/start.sh"] 
