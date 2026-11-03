package ElFabrica.Wallet_pay.audit;

import ElFabrica.Wallet_pay.common.web.RequestIdFilter;
import org.hibernate.envers.RevisionListener;
import org.slf4j.MDC;

public class AuditRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        AuditRevisionEntity revision = (AuditRevisionEntity) revisionEntity;
        revision.setUserId(CurrentActor.userId());
        revision.setRequestId(MDC.get(RequestIdFilter.MDC_KEY));
    }
}
