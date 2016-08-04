# witan.workspace

Workspace microservice for Witan

## Usage

### Production
```
lein uberjar
java -jar target/witan.workspace-standalone.jar
```

### Development
(Requires a valid environment - see [witan.gateway](https://github.com/MastodonC/witan.gateway] for a `docker-compose` file))
```
lein run -m witan.workspace.system development
```


## License

Copyright © MastodonC
