# FROM balenalib/apalis-imx6q-node:8-buster-build as intermediate

# WORKDIR /usr/src/app

# # Move package.json to filesystem
# COPY ./package.json ./

# RUN npm install -g vue-cli quasar-cli
# RUN npm install

# # Move app to filesystem
# COPY ./ ./

# # Build electron app
# RUN JOBS=MAX quasar build -m electron -t mat

# RUN npm cache clean --force && rm -rf /tmp/*


FROM bshibley/electron-base-armv7:8

# Move to app dir
WORKDIR /usr/src/app

COPY "./electron-release/linux-armv7l" /usr/src/app/
# COPY --from=intermediate /usr/src/app/dist /usr/src/app

RUN ln -s "/usr/src/app/Toradex OTA Admin/" "/usr/src/app/exec-app"
# RUN ln -s "/usr/src/app/spa-mat/Toradex OTA-linux-armv7l/Toradex OTA" "/usr/src/app/exec-app"

# Start app
CMD [ "/usr/bin/xinit", "/usr/src/app/exec-app" ]
