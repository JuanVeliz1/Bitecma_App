const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, 'Bitecma_Android');

const dirs = [
  'app/src/main/java/com/bitecma/app/data',
  'app/src/main/java/com/bitecma/app/ui/login',
  'app/src/main/java/com/bitecma/app/ui/dashboard',
  'app/src/main/java/com/bitecma/app/ui/admin',
  'app/src/main/java/com/bitecma/app/ui/theme',
  'app/src/main/res/values',
  'app/src/main/res/drawable'
];

dirs.forEach(d => fs.mkdirSync(path.join(root, d), { recursive: true }));

const files = {
  'settings.gradle.kts': `
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "BitecmaApp"
include(":app")
  `,
  'build.gradle.kts': `
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}
  `,
  'gradle.properties': `
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
  `,
  'app/build.gradle.kts': `
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bitecma.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bitecma.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
  `,
  'app/src/main/AndroidManifest.xml': `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.BitecmaApp"
        tools:targetApi="35">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.BitecmaApp">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
  `,
  'app/src/main/res/values/strings.xml': `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">BitecmaApp</string>
</resources>
  `,
  'app/src/main/res/values/themes.xml': `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.BitecmaApp" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
  `,
  'app/src/main/res/xml/backup_rules.xml': `<?xml version="1.0" encoding="utf-8"?>
<full-backup-content xmlns:android="http://schemas.android.com/apk/res/android">
    <include domain="sharedpref" path="."/>
</full-backup-content>`,
  'app/src/main/res/xml/data_extraction_rules.xml': `<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <include domain="sharedpref" path="."/>
    </cloud-backup>
</data-extraction-rules>`
};

for (const [filepath, content] of Object.entries(files)) {
  fs.writeFileSync(path.join(root, filepath), content.trim());
}

console.log("Project base created.");
