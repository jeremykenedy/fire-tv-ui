package com.jeremykenedy.firetv.ui;
public final class FactoryHomeBypassTest {
    public static void main(String[] args) {
        FactoryHomeBypass bypass = new FactoryHomeBypass();
        if (bypass.skip(true,false,1000)) throw new AssertionError("Ordinary Home was bypassed");
        bypass.arm(1000);
        if (bypass.skip(false,true,999)) throw new AssertionError();
        if (bypass.skip(true,false,999)) throw new AssertionError();
        if (bypass.skip(false,false,1001)) throw new AssertionError();
        if (!bypass.skip(true,false,1100) || !bypass.skip(true,false,1100)
                || !bypass.skip(true,false,1500)) throw new AssertionError("Initial native transition bounced");
        if (bypass.skip(true,false,2200)) throw new AssertionError("Subsequent Home was bypassed");
        bypass.arm(3000); bypass.clear();
        if (bypass.skip(true,false,3001)) throw new AssertionError("Explicit Home did not restore routing");
        bypass.arm(4000); bypass.skip(true,false,4100); bypass.skip(false,true,4200);
        if (bypass.skip(true,false,4300)) throw new AssertionError("Leaving native Home retained a bypass");
        bypass.arm(5000);
        if (bypass.skip(true,false,16000)) throw new AssertionError("Expired launch bypass survived");
        System.out.println("Factory UI temporary routing behavior passed");
    }
}
