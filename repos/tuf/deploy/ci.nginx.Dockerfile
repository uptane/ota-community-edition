from nginx:1.16.1

COPY nginx/nginx.conf /etc/nginx/nginx.conf

COPY nginx/ /cli-nginx

COPY repo/ /data/html

EXPOSE 8181 8181
