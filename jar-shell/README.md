# Command line shell to access BC Market Data service

```bash
docker build . -t monowai/bc-shell:dev
## On Windows/OSX
docker run -it --env-file ./shell.env monowai/bc-shell:dev
## On linux with bc-shell at a specific version.
docker run --add-host host.docker.internal:host-gateway \ 
  -it --env-file ./shell.env monowai/bc-shell:dev

```

```bash
bc-shell$ login mdemo@monowai.com
Password: N0w Login
2023-06-27 05:30:28,543 - Logged in mdemo@monowai.com
bc-shell$
```
