
# Easier to build against Alpine
FROM node:14-alpine as build

WORKDIR /app

# Contents of build will be copied in the next container
COPY package.json .
COPY yarn.lock .

RUN apk --no-cache add g++ gcc libgcc libstdc++ linux-headers make python
RUN yarn install --frozen-lockfile --force
COPY . /app
# There is an issue with "yarn package" with npx .
RUN yarn build --prod --ignore-scripts --prefer-offline
ENV NODE_ENV production
RUN rm -rf node_modules
RUN yarn install --production --ignore-scripts --prefer-offline

# Runtime version of Node
FROM node:14-alpine
RUN echo "...Building container runtime"

ENV NODE_ENV production
ENV HOST 0.0.0.0
ENV PORT 3000
ENV RAZZLE_PUBLIC_DIR /app/build/public
ENV SVC_POSITION http://localhost:9500/api
ENV SVC_DATA http://localhost:9510/api

RUN mkdir /app
WORKDIR /app

RUN chown node:node /app
COPY --chown=node:node --from=build /app/build /app/build
COPY --chown=node:node --from=build /app/node_modules /app/node_modules
COPY --chown=node:node --from=build /app/package.json /app/

USER node
CMD [ "node", "build/server.js" ]
