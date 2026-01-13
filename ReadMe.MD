# CRM App

## Setup Instructions

### API-Usage 

This application uses a custom API for the CRM App. 
Get the API form https://github.com/fubsi/mobsys-backend-api and follow the instructions provided there.
This API is a Hard requirement the application work without it.

### Firebase Configuration

1. Download your Firebase project's `google-services.json` file from the Firebase Console
2. Place the `google-services.json` file in the `app/` folder of your project

```
crm-app/
├── app/
│   └── google-services.json  ← Place file here
├── ...
```

3. Sync your project with Gradle

The app will now be able to connect to your Firebase project.

## Building

Build and run the app using Android Studio or Gradle:

```bash
./gradlew build
```
