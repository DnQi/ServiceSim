package io.github.hit_ices.serviceSim.service;

import io.github.hit_ices.serviceSim.prototype.MapBasedManagerService;
import org.infrastructureProvider.entities.Host;
import org.infrastructureProvider.entities.Vm;
import org.infrastructureProvider.policies.VmScheduler;

import java.util.List;

public class HostVmSchedulerManagerService extends MapBasedManagerService<Host, VmScheduler> {
    public boolean allocatePesForVm(Host host, Vm vm, List<Double> mipsShare) {
        return getManager(host).allocatePesForVm(vm, mipsShare);
    }

    public void deallocatePesForVm(Host host, Vm vm) {
        getManager(host).deallocatePesForVm(vm);
    }

    public List<Double> getAllocatedMipsForVm(Host host, Vm vm) {
        return getManager(host).getAllocatedMipsForVm(vm);
    }

    public double getTotalAllocatedMipsForVm(Host host, Vm vm) {
        return getManager(host).getTotalAllocatedMipsForVm(vm);
    }

    public double getMaxAvailableMips(Host host) {
        return getManager(host).getMaxAvailableMips();
    }

    public double getAvailableMips(Host host) {
        return getManager(host).getAvailableMips();
    }

    public List<String> getVmsMigratingIn(Host host) {
        return getManager(host).getVmsMigratingIn();
    }

    public void deallocatePesForAllVms(Host host) {
        getManager(host).deallocatePesForAllVms();
    }
}
