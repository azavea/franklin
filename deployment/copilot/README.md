# Use AWS Copilot CLI for Deployment

Deployment using [AWS Copilot CLI](https://aws.github.io/copilot-cli/) is a quick and straightforward way to deploy Franklin API (using the image from `quay.io/azavea/franklin:latest`) attached by an Aurora Serverless PostgreSQL DB to your AWS infrastructure. This document shows you steps of how to use this CLI to deploy your own Franklin APIs as a load-balanced web service, with some suggested configurations added in addition to the default settings. In the end, it willl provision an Application Load Balancer, security groups, an ECS service on Fargate to run `franklin-api` service, and attach Aurora Serverless PostgreSQL DB.

## Prerequisites
- [AWS Copilot CLI](https://aws.github.io/copilot-cli/docs/overview/)
- AWS CLI
- A name profile configured (`aws configure --profile <your profile name>`) to specify which AWS account and region to deploy your service

## Instructions
1. `copilot init`

    This command will ask you questions about your preferences for the application. After answering, it will initialize the infra to manage the containerized services, set up an ECR repository for the image to be uploaded, and will create a `/copilot/franklin-api/manifest.yml` file that we will configure further. Use the following answers to get started. At the end, when it asks if to deploy to a test environment, answer `No`.
    
    ```
    $ copilot init
    Use existing application: No
    Application name: franklin
    Workload type: Load Balanced Web Service
    Service name: franklin-api
    Dockerfile: Use an existing image instead
    Image: quay.io/azavea/franklin:latest
    Port: 9090
    ```

2. Update manifest

    Add the following line to the `/copilot/franklin-api/manifest.yml` file, which is the command to run in the image using the `entrypoint` provided by the image.

    ```
    command: ["serve", "--with-transactions", "--with-tiles", "--run-migrations"]
    ```

3. `copilot storage init`

    This command creates a new storage resource (Aurora Serverless in this case) attached to the `franklin-api` service. This will be accessible from inside the `franklin-api` service container through some environment variables. The command will ask you a set of questions. Use the following answers to get started. After running this command, the CLI will create an `/addons` subdirectory, which includes `CloudFormation` template for creating resources with outputs of Aurora Serverless DB with PostgreSQL engine.

    ```
    $ copilot storage init
    Only found one workload, defaulting to: franklin-api
    Storage type: Aurora Serverless
    Storage resource name: franklin-api-cluster
    Database engine: PostgreSQL
    Initial database name: franklin
    ```

4. Update addon template

    Add the following to the `Outputs:` section at the end of `/addons/franklin-api-cluster.yml`, since these will be exported and used as environment variables for the `franklin-api` service image to communicate with the Aurora Serverless DB attached.

    ```
    dbName:
        Description: "The DB_NAME exported as env var for the API container"
        Value: !Join ["", ['{{resolve:secretsmanager:', !Ref franklinapiclusterAuroraSecret, ":SecretString:dbname}}"]]
    dbHost:
        Description: "The DB_HOST exported as env var for the API container"
        Value: !Join ["", ['{{resolve:secretsmanager:', !Ref franklinapiclusterAuroraSecret, ":SecretString:host}}"]]
    dbUser:
        Description: "The DB_USER exported as env var for the API container"
        Value: !Join ["", ['{{resolve:secretsmanager:', !Ref franklinapiclusterAuroraSecret, ":SecretString:username}}"]]
    dbPassword:
        Description: "The DB_PASSWORD exported as env var for the API container"
        Value: !Join ["", ['{{resolve:secretsmanager:', !Ref franklinapiclusterAuroraSecret, ":SecretString:password}}"]]
    ```

5. `copilot env init`

    This will create a new environment where the serivces will live. After answering the questions as below, it will link your named profile to the application to be deployed, create the common infrastructure that's shared between the services such as a VPC, an Application Load Balancer, and an ECS Cluster etc.

    ```
    $ copilot env init
    Environment name: production
    Credential source: [profile default]
    Default environment configuration? Yes, use default.
    ```

6. `copilot deploy`

    This command will package the `manifest.yml` file and addons into `CloudFormation`, and create and/or update the ECS task definition and service. If all goes well, it should show something like the following in the end.

    ```
    $ copilot deploy
    Only found one workload, defaulting to: franklin-api
    Only found one environment, defaulting to: production
    Environment production is already on the latest version v1.6.1, skip upgrade.
    ✔ Proposing infrastructure changes for stack franklin-production-franklin-api 
    - Creating the infrastructure for stack franklin-production-franklin-api              [create complete]  [1103.0s]
    - An Addons CloudFormation Stack for your additional AWS resources                  [create complete]  [972.5s]
        - A Secrets Manager secret to store your DB credentials                           [create complete]  [2.9s]
        - A DB parameter group for engine configuration values                            [create complete]  [300.6s]
        - A security group for your DB cluster franklinapicluster                         [create complete]  [9.1s]
        - The franklinapicluster Aurora Serverless database cluster                       [create complete]  [365.7s]
        - A security group for your workload to access the DB cluster franklinapicluster  [create complete]  [4.5s]
    - Service discovery for your services to communicate within the VPC                 [create complete]  [0.0s]
    - Update your environment's shared resources                                        [update complete]  [214.4s]
        - A security group for your load balancer allowing HTTP and HTTPS traffic         [create complete]  [3.9s]
        - An Application Load Balancer to distribute public traffic to your services      [create complete]  [182.2s]
    - An IAM Role for the Fargate agent to make AWS API calls on your behalf            [create complete]  [13.1s]
    - A CloudWatch log group to hold your service logs                                  [create complete]  [0.2s]
    - An ECS service to run and maintain your tasks in the environment cluster          [create complete]  [87.9s]
        Deployments                                                                                           
                Revision  Rollout      Desired  Running  Failed  Pending                                           
        PRIMARY  3         [completed]  1        1        0       0                                                 
    - A target group to connect the load balancer to your service                       [create complete]  [0.0s]
    - An ECS task definition to group your containers and run them on ECS               [create complete]  [3.6s]
    - An IAM role to control permissions for the containers in your tasks               [create complete]  [16.6s]
    ✔ Deployed service franklin-api.
    Recommended follow-up action:
        You can access your service at <URL> over the internet.
    ```

7. Some commands to check the service
    - `copilot svc show`: This command shows info about the deployed services, including the endpoints, capacity and related resources per environment.
    - `copilot svc status`: This command shows the health statuses, e.g. service status, task status, and related CloudWatch alarms etc.
    - `copilot svc logs`: This command shows the the logs of the deployed service.