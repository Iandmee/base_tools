<resources>
    <#if !isNewProject>
    <string name="title_${simpleName}">${escapeXmlString(activityTitle)}</string>
    </#if>

    <!-- Strings related to login -->
    <string name="prompt_email">Email</string>
    <string name="prompt_password">Password (optional)</string>
    <string name="action_sign_in">Sign in or register</string>
    <string name="action_sign_in_short">Sign in</string>
<#if includeGooglePlus>    <string name="plus_sign_out">Switch Google+ account</string>
    <string name="plus_disconnect">Disconnect from Google+</string></#if>
    <string name="error_invalid_email">This email address is invalid</string>
    <string name="error_invalid_password">This password is too short</string>
    <string name="error_incorrect_password">This password is incorrect</string>
    <string name="error_field_required">This field is required</string>
</resources>
