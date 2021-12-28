#!/bin/bash
export SERVICE_NAME=fp-poker-backend
export REGION=europe-north1
export PROJECT=evo-bootcamp-2021-fp-poker
export IMAGE_NAME=gcr.io/$PROJECT/$SERVICE_NAME:latest
docker build -t $IMAGE_NAME .
#gcloud auth activate-service-account bitbucket-deployer@mubertgcp.iam.gserviceaccount.com --key-file=credentials/service-account.json
gcloud config list
gcloud auth configure-docker -q
docker push $IMAGE_NAME