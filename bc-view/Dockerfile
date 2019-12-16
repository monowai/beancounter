
# Easier to build against Alpine
FROM node:12-alpine as builder

WORKDIR /app

RUN apk --no-cache --virtual build-dependencies add \
    python \
    make \
    g++ \
    && apk del build-dependencies

# Node dev dependency  for building other dependencies
#RUN npm install -g node-gyp && npm info node-gyp | grep version
#RUN yarn install --frozen-lockfile --force
# Contents of build will be copied in the next container
COPY . /app
RUN yarn install --frozen-lockfile --force
RUN yarn build --frozen-lockfile --force

# Runtime version of Node
FROM node:12-slim
RUN echo "Building Runtime Container"
USER node

ENV NODE_ENV production
ENV HOST 0.0.0.0
ENV PORT 3000
ENV RAZZLE_PUBLIC_DIR build/public

WORKDIR /app
RUN chown node:node /app
COPY --chown=node:node --from=builder /app/build /app/build
COPY --chown=node:node --from=builder /app/package.json /app/
COPY --chown=node:node --from=builder /app/yarn.lock /app/

# node_modules needs only the production runtime dependencies
# Razzle defualt webpack doesn't pack node_modules into ./build
RUN yarn install --frozen-lockfile && \
    yarn cache clean --force && \
    rm yarn.lock

CMD ["node", "build/server.js"]