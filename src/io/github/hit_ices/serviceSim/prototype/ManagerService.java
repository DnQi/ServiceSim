package io.github.hit_ices.serviceSim.prototype;

public interface ManagerService<TEntity, TManager> {
    TManager getManager(TEntity mangedEntity);
    TManager manage(TEntity mangedEntity);
    void unManage(TEntity managedEntity);
}
