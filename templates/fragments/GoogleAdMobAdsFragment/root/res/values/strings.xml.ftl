<resources>
    <string name="action_settings">Settings</string>

    <#if adFormat == "banner">
    <string name="hello_world">Hello world!</string>
    <!-- -
        This is an ad unit ID for a banner test ad. Replace with your own banner ad unit id.
        For more information, see https://support.google.com/admob/answer/3052638
    <!- -->
    <string name="banner_ad_unit_id">YOUR_BANNER_AD_UNIT_ID</string>
    <#elseif adFormat == "interstitial">
    <string name="interstitial_ad_sample">Interstitial Ad Sample</string>
    <string name="start_level">Level 1</string>
    <string name="next_level">Next Level</string>
    <string name="level_text">Level %1$d</string>
    <!-- -
        This is an ad unit ID for an interstitial test ad. Replace with your own interstitial ad unit id.
        For more information, see https://support.google.com/admob/answer/3052638
    <!- -->
    <string name="interstitial_ad_unit_id">YOUR_INTERSTITIAL_AD_UNIT_ID</string>
    </#if>

</resources>
