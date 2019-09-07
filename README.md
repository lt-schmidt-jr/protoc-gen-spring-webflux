# protoc-gen-spring-webflux
gRPC to JSON proxy generator protoc plugin for Spring WebFlux.

The protoc-gen-spring-webflux is a plugin of the Google protocol buffers compiler
[protoc](https://github.com/protocolbuffers/protobuf).
It reads protobuf service definitions and generates a Spring WebFlux routing which
translates RPC and RESTful HTTP API into gRPC.

## Installation
* Download the latest binaries from [Release](https://github.com/disc99/protoc-gen-spring-webflux/releases).
* Since the protoc plugin is a jar file, it is recommended to use it from an [external script](https://github.com/disc99/protoc-gen-spring-webflux/blob/master/protoc-gen-spring-webflux).

## Usage

1. Define your [gRPC](https://grpc.io/docs/) service using protocol buffers.

```protoc:example.proto
syntax = "proto3";

package example.demo;

// messages...

service EchoService {

    rpc GetEcho(EchoRequest) returns (EchoResponse) {}

    rpc CreateEcho(CreateEchoRequest) returns (CreateEchoResponse) {}
}
```
2. (Optional) Add a [`google.api.http`](https://github.com/googleapis/googleapis/blob/master/google/api/http.proto#L46) annotation to your .proto file for REST style API.

```diff
syntax = "proto3";

package example.demo;
+
+import "google/api/annotations.proto";
+

// messages...

service EchoService {

-    rpc GetEcho(EchoRequest) returns (EchoResponse) {}
+    rpc GetEcho(EchoRequest) returns (EchoResponse) {
+        // If you use REST API style
+        option (google.api.http) = {
+            get: "/echo/{echo}"
+        };
+    }

-    rpc CreateEcho(CreateEchoRequest) returns (CreateEchoResponse) {
+    rpc CreateEcho(CreateEchoRequest) returns (CreateEchoResponse) {
+        // If you use REST API style
+        option (google.api.http) = {
+              post: "/echo"
+              body: "*"
+        };
+    }
}
```

3. Generate routing handler class using `protoc-gen-spring-webflux`

```bash
# When default configuration, provide RPC style API.
# In case of RPC style, API of grpc-web format(/{package}.{service}/{method}) is provided.
# ex. POST http://hostname/example.demo.EchoService/GetEcho
protoc -I. \
    --spring-webflux_out=. \
     example.proto

# If you use google.api.http and REST API style.
protoc -I. \
    -I$APIPATH/googleapis \
    --plugin=./protoc-gen-spring-webflux \
    --spring-webflux_out=style=rest:. \
     example.proto     
```

4. Write an routing of the Spring WebFlux

```java:WebConfg.java
@Configuration
class WebConfig {
	@Bean
	EchoServiceHandler exampleHandlers() {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(/*...*/)
				.usePlaintext()
				.build();
		EchoServiceGrpc.EchoServiceBlockingStub stub = EchoServiceGrpc.newBlockingStub(channel);
		return new EchoServiceHandler(stub);
	}

	@Bean
	RouterFunction<ServerResponse> routing(EchoServiceHandler handler) {
		return RouterFunctions
            // Use the handleAll method to route everything to the generated Handler.
            .route(path("/echo*/**"), handler::handleAll)
            // Handler can be routed individually by using the generated method.
            .andRoute(GET("/echo/{id}"), handler::getEcho)
            .andRoute(POST("/echo"), handler::createEcho)
        ;
    }
}
```


## Missing Features Shortlist
* Streams not supported.
* Custom patterns not supported.
* Variables not supported.
* Not supporting * and ** in path.
