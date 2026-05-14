package com.kubiki.themis.grpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.62.2)",
    comments = "Source: themis.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ActionServiceGrpc {

  private ActionServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "themis.ActionService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.kubiki.themis.grpc.ResourceRequest,
      com.kubiki.themis.grpc.ActionList> getGetExecutableActionsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetExecutableActions",
      requestType = com.kubiki.themis.grpc.ResourceRequest.class,
      responseType = com.kubiki.themis.grpc.ActionList.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.kubiki.themis.grpc.ResourceRequest,
      com.kubiki.themis.grpc.ActionList> getGetExecutableActionsMethod() {
    io.grpc.MethodDescriptor<com.kubiki.themis.grpc.ResourceRequest, com.kubiki.themis.grpc.ActionList> getGetExecutableActionsMethod;
    if ((getGetExecutableActionsMethod = ActionServiceGrpc.getGetExecutableActionsMethod) == null) {
      synchronized (ActionServiceGrpc.class) {
        if ((getGetExecutableActionsMethod = ActionServiceGrpc.getGetExecutableActionsMethod) == null) {
          ActionServiceGrpc.getGetExecutableActionsMethod = getGetExecutableActionsMethod =
              io.grpc.MethodDescriptor.<com.kubiki.themis.grpc.ResourceRequest, com.kubiki.themis.grpc.ActionList>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetExecutableActions"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kubiki.themis.grpc.ResourceRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kubiki.themis.grpc.ActionList.getDefaultInstance()))
              .setSchemaDescriptor(new ActionServiceMethodDescriptorSupplier("GetExecutableActions"))
              .build();
        }
      }
    }
    return getGetExecutableActionsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.kubiki.themis.grpc.ActionRequest,
      com.kubiki.themis.grpc.ValidationResponse> getValidatePreconditionsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ValidatePreconditions",
      requestType = com.kubiki.themis.grpc.ActionRequest.class,
      responseType = com.kubiki.themis.grpc.ValidationResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.kubiki.themis.grpc.ActionRequest,
      com.kubiki.themis.grpc.ValidationResponse> getValidatePreconditionsMethod() {
    io.grpc.MethodDescriptor<com.kubiki.themis.grpc.ActionRequest, com.kubiki.themis.grpc.ValidationResponse> getValidatePreconditionsMethod;
    if ((getValidatePreconditionsMethod = ActionServiceGrpc.getValidatePreconditionsMethod) == null) {
      synchronized (ActionServiceGrpc.class) {
        if ((getValidatePreconditionsMethod = ActionServiceGrpc.getValidatePreconditionsMethod) == null) {
          ActionServiceGrpc.getValidatePreconditionsMethod = getValidatePreconditionsMethod =
              io.grpc.MethodDescriptor.<com.kubiki.themis.grpc.ActionRequest, com.kubiki.themis.grpc.ValidationResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ValidatePreconditions"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kubiki.themis.grpc.ActionRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kubiki.themis.grpc.ValidationResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ActionServiceMethodDescriptorSupplier("ValidatePreconditions"))
              .build();
        }
      }
    }
    return getValidatePreconditionsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.kubiki.themis.grpc.ActionRequest,
      com.kubiki.themis.grpc.ExecutionStatus> getExecuteRemediationMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ExecuteRemediation",
      requestType = com.kubiki.themis.grpc.ActionRequest.class,
      responseType = com.kubiki.themis.grpc.ExecutionStatus.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<com.kubiki.themis.grpc.ActionRequest,
      com.kubiki.themis.grpc.ExecutionStatus> getExecuteRemediationMethod() {
    io.grpc.MethodDescriptor<com.kubiki.themis.grpc.ActionRequest, com.kubiki.themis.grpc.ExecutionStatus> getExecuteRemediationMethod;
    if ((getExecuteRemediationMethod = ActionServiceGrpc.getExecuteRemediationMethod) == null) {
      synchronized (ActionServiceGrpc.class) {
        if ((getExecuteRemediationMethod = ActionServiceGrpc.getExecuteRemediationMethod) == null) {
          ActionServiceGrpc.getExecuteRemediationMethod = getExecuteRemediationMethod =
              io.grpc.MethodDescriptor.<com.kubiki.themis.grpc.ActionRequest, com.kubiki.themis.grpc.ExecutionStatus>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ExecuteRemediation"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kubiki.themis.grpc.ActionRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kubiki.themis.grpc.ExecutionStatus.getDefaultInstance()))
              .setSchemaDescriptor(new ActionServiceMethodDescriptorSupplier("ExecuteRemediation"))
              .build();
        }
      }
    }
    return getExecuteRemediationMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ActionServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ActionServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ActionServiceStub>() {
        @java.lang.Override
        public ActionServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ActionServiceStub(channel, callOptions);
        }
      };
    return ActionServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ActionServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ActionServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ActionServiceBlockingStub>() {
        @java.lang.Override
        public ActionServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ActionServiceBlockingStub(channel, callOptions);
        }
      };
    return ActionServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ActionServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ActionServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ActionServiceFutureStub>() {
        @java.lang.Override
        public ActionServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ActionServiceFutureStub(channel, callOptions);
        }
      };
    return ActionServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void getExecutableActions(com.kubiki.themis.grpc.ResourceRequest request,
        io.grpc.stub.StreamObserver<com.kubiki.themis.grpc.ActionList> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetExecutableActionsMethod(), responseObserver);
    }

    /**
     */
    default void validatePreconditions(com.kubiki.themis.grpc.ActionRequest request,
        io.grpc.stub.StreamObserver<com.kubiki.themis.grpc.ValidationResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getValidatePreconditionsMethod(), responseObserver);
    }

    /**
     */
    default void executeRemediation(com.kubiki.themis.grpc.ActionRequest request,
        io.grpc.stub.StreamObserver<com.kubiki.themis.grpc.ExecutionStatus> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getExecuteRemediationMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ActionService.
   */
  public static abstract class ActionServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ActionServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ActionService.
   */
  public static final class ActionServiceStub
      extends io.grpc.stub.AbstractAsyncStub<ActionServiceStub> {
    private ActionServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ActionServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ActionServiceStub(channel, callOptions);
    }

    /**
     */
    public void getExecutableActions(com.kubiki.themis.grpc.ResourceRequest request,
        io.grpc.stub.StreamObserver<com.kubiki.themis.grpc.ActionList> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetExecutableActionsMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void validatePreconditions(com.kubiki.themis.grpc.ActionRequest request,
        io.grpc.stub.StreamObserver<com.kubiki.themis.grpc.ValidationResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getValidatePreconditionsMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void executeRemediation(com.kubiki.themis.grpc.ActionRequest request,
        io.grpc.stub.StreamObserver<com.kubiki.themis.grpc.ExecutionStatus> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getExecuteRemediationMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ActionService.
   */
  public static final class ActionServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ActionServiceBlockingStub> {
    private ActionServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ActionServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ActionServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.kubiki.themis.grpc.ActionList getExecutableActions(com.kubiki.themis.grpc.ResourceRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetExecutableActionsMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.kubiki.themis.grpc.ValidationResponse validatePreconditions(com.kubiki.themis.grpc.ActionRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getValidatePreconditionsMethod(), getCallOptions(), request);
    }

    /**
     */
    public java.util.Iterator<com.kubiki.themis.grpc.ExecutionStatus> executeRemediation(
        com.kubiki.themis.grpc.ActionRequest request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getExecuteRemediationMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ActionService.
   */
  public static final class ActionServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<ActionServiceFutureStub> {
    private ActionServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ActionServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ActionServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.kubiki.themis.grpc.ActionList> getExecutableActions(
        com.kubiki.themis.grpc.ResourceRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetExecutableActionsMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.kubiki.themis.grpc.ValidationResponse> validatePreconditions(
        com.kubiki.themis.grpc.ActionRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getValidatePreconditionsMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_GET_EXECUTABLE_ACTIONS = 0;
  private static final int METHODID_VALIDATE_PRECONDITIONS = 1;
  private static final int METHODID_EXECUTE_REMEDIATION = 2;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_GET_EXECUTABLE_ACTIONS:
          serviceImpl.getExecutableActions((com.kubiki.themis.grpc.ResourceRequest) request,
              (io.grpc.stub.StreamObserver<com.kubiki.themis.grpc.ActionList>) responseObserver);
          break;
        case METHODID_VALIDATE_PRECONDITIONS:
          serviceImpl.validatePreconditions((com.kubiki.themis.grpc.ActionRequest) request,
              (io.grpc.stub.StreamObserver<com.kubiki.themis.grpc.ValidationResponse>) responseObserver);
          break;
        case METHODID_EXECUTE_REMEDIATION:
          serviceImpl.executeRemediation((com.kubiki.themis.grpc.ActionRequest) request,
              (io.grpc.stub.StreamObserver<com.kubiki.themis.grpc.ExecutionStatus>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getGetExecutableActionsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.kubiki.themis.grpc.ResourceRequest,
              com.kubiki.themis.grpc.ActionList>(
                service, METHODID_GET_EXECUTABLE_ACTIONS)))
        .addMethod(
          getValidatePreconditionsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.kubiki.themis.grpc.ActionRequest,
              com.kubiki.themis.grpc.ValidationResponse>(
                service, METHODID_VALIDATE_PRECONDITIONS)))
        .addMethod(
          getExecuteRemediationMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              com.kubiki.themis.grpc.ActionRequest,
              com.kubiki.themis.grpc.ExecutionStatus>(
                service, METHODID_EXECUTE_REMEDIATION)))
        .build();
  }

  private static abstract class ActionServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ActionServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.kubiki.themis.grpc.ThemisProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ActionService");
    }
  }

  private static final class ActionServiceFileDescriptorSupplier
      extends ActionServiceBaseDescriptorSupplier {
    ActionServiceFileDescriptorSupplier() {}
  }

  private static final class ActionServiceMethodDescriptorSupplier
      extends ActionServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    ActionServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (ActionServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ActionServiceFileDescriptorSupplier())
              .addMethod(getGetExecutableActionsMethod())
              .addMethod(getValidatePreconditionsMethod())
              .addMethod(getExecuteRemediationMethod())
              .build();
        }
      }
    }
    return result;
  }
}
