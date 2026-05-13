# Action Execution & Enforcement (Themis)

Themis is the stateless executor ("Hands") in the AMoCNA project. It consumes actions from RabbitMQ and executes them via REST, SHELL, or gRPC.

## Running Locally with Docker

You can launch Themis along with its dependencies (RabbitMQ, GraphDB, Prometheus) using Docker Compose.

### Prerequisites
- Docker and Docker Compose installed.
- RabbitMQ Delayed Message Exchange plugin (handled automatically by our Dockerfile).

### Launching the Environment

Navigate to the `modules/themis` directory and run:

```bash
docker-compose up --build
```

This will:
1. **Build Themis** using a multi-stage Java 25 image.
2. **Build RabbitMQ** with the `rabbitmq_delayed_message_exchange` plugin enabled.
3. **Start GraphDB** for the Knowledge Base.
4. **Start Prometheus** for condition evaluation.

### Services and Ports
- **Themis:** `8080` (HTTP), `50051` (gRPC)
- **RabbitMQ Management:** `15672` (guest/guest)
- **GraphDB Workbench:** `7200`
- **Prometheus:** `9090`

## Queue Configuration

Themis listens on the `amocna.action.queue` and sends status updates to `amocna.status.queue`. All messages are JSON formatted.
