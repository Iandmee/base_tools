package ${packageName};

import android.app.Activity;
import android.os.Bundle;

/*
 * Details activity class that loads LeanbackDetailsFragment class
 */
public class ${detailsActivity} extends Activity {
    public static final String SHARED_ELEMENT_NAME = "hero";
    public static final String MOVIE = "Movie";

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.${detailsLayoutName});
    }

}
