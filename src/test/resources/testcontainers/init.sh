echo " - Create pn-delayer TABLES"

aws --profile default --region us-east-1 --endpoint-url=http://localstack:4566 \
    dynamodb create-table \
    --table-name pn-PaperTrackingsErrors  \
    --attribute-definitions \
        AttributeName=requestId,AttributeType=S \
        AttributeName=created,AttributeType=S \
    --key-schema \
        AttributeName=requestId,KeyType=HASH \
        AttributeName=created,KeyType=RANGE \
    --provisioned-throughput \
        ReadCapacityUnits=10,WriteCapacityUnits=5

echo "Initialization terminated"
