package io.github.junhezhang.qingjian;
import org.junit.Test;
import static org.junit.Assert.*;

public class TodoTest {
    private Todo task(){Todo t=new Todo();t.due=100_000_000L;t.leadMinutes=60;return t;}
    @Test public void thresholdAndDeadline(){Todo t=task();assertEquals(0,t.reminderStage(t.due-3_600_001));assertEquals(1,t.reminderStage(t.due-3_600_000));t.notifiedStage=1;assertEquals(0,t.reminderStage(t.due-1));assertEquals(2,t.reminderStage(t.due));t.notifiedStage=2;assertEquals(0,t.reminderStage(t.due+1));assertEquals(Long.MAX_VALUE,t.nextWake(t.due));}
    @Test public void completedAndDisabledNeverSchedule(){Todo t=task();t.done=true;assertEquals(0,t.reminderStage(t.due));assertEquals(Long.MAX_VALUE,t.nextWake(0));t.done=false;t.leadMinutes=-1;assertEquals(0,t.reminderStage(t.due));assertEquals(Long.MAX_VALUE,t.nextWake(0));t.leadMinutes=0;t.due=0;assertEquals(Long.MAX_VALUE,t.nextWake(0));}
    @Test public void snoozeCanCrossDeadline(){Todo t=task();long start=t.due-300_000;t.snooze(start);assertEquals(start+600_000,t.nextWake(start));assertEquals(0,t.reminderStage(t.due));assertEquals(2,t.reminderStage(start+600_000));}
    @Test public void undoCompletionRearms(){Todo t=task();t.notifiedStage=2;t.done=true;t.setDone(false);assertEquals(0,t.notifiedStage);assertEquals(2,t.reminderStage(t.due));}
    @Test public void nextAlarmChoosesUpcomingThenDue(){Todo t=task();assertEquals(t.due-3_600_000,t.nextWake(0));t.notifiedStage=1;assertEquals(t.due,t.nextWake(0));assertEquals(t.due+1000,t.nextWake(t.due));}
    @Test public void dueOnlyReminderSkipsUpcomingStage(){Todo t=task();t.leadMinutes=0;assertEquals(0,t.reminderStage(t.due-1));assertEquals(2,t.reminderStage(t.due));}
}
