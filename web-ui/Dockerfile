FROM node:14 as builder
WORKDIR /app
COPY ./ /app
RUN yarn global add @quasar/cli@1.2.2
RUN yarn global add  jest@26.4.0
RUN yarn install --frozen-lockfile
RUN rm -fr ./docker-context/dist; mkdir -p ./docker-context/dist
RUN jest
RUN quasar build

RUN cp -r /app/dist/spa/* /app/docker-context/dist

FROM nginx:latest as ota-nginx
ARG CI_COMMIT_SHA
COPY --from=builder /app/docker-context/confs/nginx.conf /etc/nginx/conf.d/app.template
COPY --from=builder /app/docker-context/confs/default.conf /etc/nginx/nginx.conf
COPY --from=builder /app/docker-context/dist /usr/app
COPY --from=builder /app/docker-context/proxy.txt /proxy.txt
COPY --from=builder /app/docker-context/start.sh /start.sh
RUN sed -i "s#src=js/vendor#src=https://CDN_URL/js/$CI_COMMIT_SHA/vendor#g" /usr/app/index.html && \
    sed -i "s#src=js/runtime#src=https://CDN_URL/js/$CI_COMMIT_SHA/runtime#g" /usr/app/index.html
EXPOSE 80
ENTRYPOINT ["/start.sh"]

