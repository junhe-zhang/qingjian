package io.github.junhezhang.qingjian;
import android.app.job.*;

public class SyncJob extends JobService {
    @Override public boolean onStartJob(JobParameters params){return SyncManager.start(this,false,null,conflict->jobFinished(params,false));}
    @Override public boolean onStopJob(JobParameters params){return false;}
}
