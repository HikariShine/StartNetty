import com.bugsnag.Bugsnag;

/**
 * Created by Administrator on 2017/9/14.
 */
public class TestSwitch {

    enum DecoderState {
        READ_FIXED_HEADER,
        READ_VARIABLE_HEADER,
        READ_PAYLOAD,
        BAD_MESSAGE,
    }

    private DecoderState state;

    public DecoderState get() {
        if (state == DecoderState.READ_FIXED_HEADER)
            return DecoderState.READ_VARIABLE_HEADER;
        return DecoderState.READ_FIXED_HEADER;
    }

    public void test() {

        switch (get()) {
            case READ_FIXED_HEADER:
                System.out.println(1);
            case READ_VARIABLE_HEADER:
                System.out.println(2);
        }

    }

    public static void main(String[] args) {
        Bugsnag bugsnag = new Bugsnag("eb885942b8cc3c9079af7ad8cabd323d");
        bugsnag.notify(new RuntimeException("Non-fatal"));
        TestSwitch t = new TestSwitch();
        t.test();
    }

}
