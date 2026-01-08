FROM node:10.14.2 as intermediate

WORKDIR /app

ADD "./package-ci-unit.json" "./package.json"


RUN yarn global add jest-cli
RUN yarn add jest-serializer-vue
RUN yarn 
# RUN  rm -fr ./node_modules



