mvn clean install -DskipTests=true
docker login registry.smartvendingmachines.net -u payment -p Payment@123
docker build -f Dockerfile -t registry.smartvendingmachines.net/payment-prod/identity-service .
docker push registry.smartvendingmachines.net/payment-prod/identity-service
