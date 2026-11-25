package com.packages.fragmentdemo;

import com.android.app.Activity;
import com.android.app.Fragment;
import com.android.view.LayoutInflater;
import com.android.view.View;
import com.android.view.ViewGroup;
import com.android.widget.TextView;

/** Click the button to replace the page without opening another Activity or window. */
public class MainActivity extends Activity {
    @Override protected void onCreate() {
        super.onCreate();
        getWindow().setLayout(480, 360);
        setContentView(R.layout.activity_main);
        getFragmentManager().beginTransaction()
                .add(R.id.content, new PageFragment(false), "first").commitNow();
    }

    public static class PageFragment extends Fragment {
        private final boolean second;
        public PageFragment(boolean second) { this.second = second; }
        @Override public View onCreateView(LayoutInflater inflater, ViewGroup container) {
            return inflater.inflate(R.layout.fragment_page, container, false);
        }
        @Override public void onViewCreated(View view) {
            ((TextView) view.findViewById(R.id.title)).setText(second ? "Second fragment" : "First fragment");
            view.findViewById(R.id.switch_page).setOnClickListener(button ->
                    getFragmentManager().beginTransaction()
                            .replace(R.id.content, new PageFragment(!second), second ? "first" : "second")
                            .commit());
        }
    }
}
