<#import "./shared_macros.ftl" as shared>
apply plugin: 'com.android.application'

<@shared.androidConfig hasApplicationId=true applicationId=instantAppPackageName+'.app' canUseProguard=true/>

dependencies {
    implementation project(':${projectName}')
    implementation project(':${baseFeatureName}')
    <@shared.watchProjectDependencies/>
}
