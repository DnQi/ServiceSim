package io.github.hit_ices.serviceSim.service;

import org.cloudbus.cloudsim.Log;
import org.infrastructureProvider.entities.Host;
import org.infrastructureProvider.entities.Vm;
import org.infrastructureProvider.policies.provisioners.VmResourceProvisioner;

import java.util.List;

// HostManager class with methods moved from Host
public class HostManager {
    protected final VmCloudletSchedulerManagerService vmCloudletSchedulerManagerService;
    protected final VmResourceProvisioner<Integer> ramProvisioner;
    protected final VmResourceProvisioner<Long> bwProvisioner;
    protected final VmResourceProvisioner<Double> mipsProvisioner;

    public HostVmSchedulerManagerService getHostVmSchedulerManagerService() {
        return hostVmSchedulerManagerService;
    }

    protected final HostVmSchedulerManagerService hostVmSchedulerManagerService;

    public HostManager(VmCloudletSchedulerManagerService vmCloudletSchedulerManagerService,
                       VmResourceProvisioner<Integer> ramProvisioner,
                       VmResourceProvisioner<Long> bwProvisioner,
                       VmResourceProvisioner<Double> mipsProvisioner,
                       HostVmSchedulerManagerService hostVmSchedulerManagerService) {
        this.vmCloudletSchedulerManagerService = vmCloudletSchedulerManagerService;
        this.ramProvisioner = ramProvisioner;
        this.bwProvisioner = bwProvisioner;
        this.mipsProvisioner = mipsProvisioner;
        this.hostVmSchedulerManagerService = hostVmSchedulerManagerService;
    }

    public double updateVmsProcessing(Host host, double currentTime) {
        double smallerTime = Double.MAX_VALUE;

        for (Vm vm : host.getVmList()) {
            double time = vmCloudletSchedulerManagerService.updateVmProcessing(vm, currentTime, hostVmSchedulerManagerService.getManager(host).getAllocatedMipsForVm(vm));
            if (time > 0.0 && time < smallerTime) {
                smallerTime = time;
            }
        }
        return smallerTime;
    }

    public void addMigratingInVm(Host host, Vm vm, List<Vm> vmsMigratingIn, long storage) {
        vm.setInMigration(true);

        if (!vmsMigratingIn.contains(vm)) {
            if (storage < vm.getSize()) {
                Log.printLine("[VmScheduler.addMigratingInVm] Allocation of VM #" + vm.getId() + " failed by storage");
                System.exit(0);
            }

            if (!ramProvisioner.allocateForVm(host, vm, vmCloudletSchedulerManagerService.getCurrentRequestedRam(vm))) {
                Log.printLine("[VmScheduler.addMigratingInVm] Allocation of VM #" + vm.getId() + " failed by RAM");
                System.exit(0);
            }

            if (!bwProvisioner.allocateForVm(host, vm, vmCloudletSchedulerManagerService.getCurrentRequestedBw(vm))) {
                Log.printLine("[VmScheduler.addMigratingInVm] Allocation of VM #" + vm.getId() + " failed by BW");
                System.exit(0);
            }

            hostVmSchedulerManagerService.getManager(host).getVmsMigratingIn().add(vm.getUid());
            if (!hostVmSchedulerManagerService.getManager(host).allocatePesForVm(vm, vmCloudletSchedulerManagerService.getCurrentRequestedMips(vm))) {
                Log.printLine("[VmScheduler.addMigratingInVm] Allocation of VM #" + vm.getId() + " failed by MIPS");
                System.exit(0);
            }

            storage -= vm.getSize();
            vmsMigratingIn.add(vm);
        }
    }

    public void removeMigratingInVm(Host host, Vm vm, List<Vm> vmsMigratingIn, List<? extends Vm> vmList) {
        vmDeallocate(host, vm);
        vmsMigratingIn.remove(vm);
        vmList.remove(vm);
        hostVmSchedulerManagerService.getManager(host).getVmsMigratingIn().remove(vm.getUid());
        vm.setInMigration(false);
    }

    public void reallocateMigratingInVms(Host host, List<Vm> vmsMigratingIn) {
        var vmList = host.getVmList();
        for (Vm vm : vmsMigratingIn) {
            if (!vmList.contains(vm)) {
                vmList.add(vm);
            }
            if (!hostVmSchedulerManagerService.getManager(host).getVmsMigratingIn().contains(vm.getUid())) {
                hostVmSchedulerManagerService.getManager(host).getVmsMigratingIn().add(vm.getUid());
            }
            ramProvisioner.allocateForVm(host, vm, vmCloudletSchedulerManagerService.getCurrentRequestedRam(vm));
            bwProvisioner.allocateForVm(host, vm, vmCloudletSchedulerManagerService.getCurrentRequestedBw(vm));
            hostVmSchedulerManagerService.getManager(host).allocatePesForVm(vm, vmCloudletSchedulerManagerService.getCurrentRequestedMips(vm));
        }
    }

    public boolean isSuitableForVm(Host host, Vm vm) {
        return (hostVmSchedulerManagerService.getManager(host).getPeCapacity() >= vmCloudletSchedulerManagerService.getCurrentRequestedMaxMips(vm)
                && hostVmSchedulerManagerService.getManager(host).getAvailableMips() >= vmCloudletSchedulerManagerService.getCurrentRequestedTotalMips(vm)
                && ramProvisioner.isSuitableForVm(host, vm, vmCloudletSchedulerManagerService.getCurrentRequestedRam(vm))
                && bwProvisioner.isSuitableForVm(host, vm, vmCloudletSchedulerManagerService.getCurrentRequestedBw(vm)));
    }

    public boolean vmCreate(Host host, Vm vm, long storage) {
        if (storage < vm.getSize()) {
            Log.printLine("[VmScheduler.vmCreate] Allocation of VM #" + vm.getId() + " failed by storage");
            return false;
        }

        if (!ramProvisioner.allocateForVm(host, vm, vmCloudletSchedulerManagerService.getCurrentRequestedRam(vm))) {
            Log.printLine("[VmScheduler.vmCreate] Allocation of VM #" + vm.getId() + " failed by RAM");
            return false;
        }

        if (!bwProvisioner.allocateForVm(host, vm, vmCloudletSchedulerManagerService.getCurrentRequestedBw(vm))) {
            Log.printLine("[VmScheduler.vmCreate] Allocation of VM #" + vm.getId() + " failed by BW");
            ramProvisioner.deallocateForVm(host, vm);
            return false;
        }

        if (!hostVmSchedulerManagerService.getManager(host).allocatePesForVm(vm, vmCloudletSchedulerManagerService.getCurrentRequestedMips(vm))) {
            Log.printLine("[VmScheduler.vmCreate] Allocation of VM #" + vm.getId() + " failed by MIPS");
            ramProvisioner.deallocateForVm(host, vm);
            bwProvisioner.deallocateForVm(host, vm);
            return false;
        }

        storage -= vm.getSize();
        host.getVmList().add(vm);
        vm.setHost(host);
        return true;
    }

    public void vmDestroy(Host host, Vm vm, List<? extends Vm> vmList) {
        if (vm != null) {
            vmDeallocate(host, vm);
            vmList.remove(vm);
            vm.setHost(null);
        }
    }

    public void vmDestroyAll(Host host, List<? extends Vm> vmList, long storage) {
        vmDeallocateAll(host);
        for (Vm vm : vmList) {
            vm.setHost(null);
            storage += vm.getSize();
        }
        vmList.clear();
    }

    protected void vmDeallocate(Host host, Vm vm) {
        ramProvisioner.deallocateForVm(host, vm);
        bwProvisioner.deallocateForVm(host, vm);
        hostVmSchedulerManagerService.getManager(host).deallocatePesForVm(vm);
    }

    protected void vmDeallocateAll(Host host) {
        ramProvisioner.deallocateForAllVms(host);
        bwProvisioner.deallocateForAllVms(host);
        hostVmSchedulerManagerService.getManager(host).deallocatePesForAllVms();
    }

    /**
     * Allocates PEs for a VM.
     *
     * @param vm        the vm
     * @param mipsShare the mips share
     * @return $true if this policy allows a new VM in the host, $false otherwise
     * @pre $none
     * @post $none
     */
    public boolean allocatePesForVm(Host host, Vm vm, List<Double> mipsShare) {
        return hostVmSchedulerManagerService.getManager(host).allocatePesForVm(vm, mipsShare);
    }

    /**
     * Releases PEs allocated to a VM.
     *
     * @param vm the vm
     * @pre $none
     * @post $none
     */
    public void deallocatePesForVm(Host host, Vm vm) {
        hostVmSchedulerManagerService.getManager(host).deallocatePesForVm(vm);
    }

    /**
     * Returns the MIPS share of each Pe that is allocated to a given VM.
     *
     * @param vm the vm
     * @return an array containing the amount of MIPS of each pe that is available to the VM
     * @pre $none
     * @post $none
     */
    public List<Double> getAllocatedMipsForVm(Host host, Vm vm) {
        return hostVmSchedulerManagerService.getManager(host).getAllocatedMipsForVm(vm);
    }

    /**
     * Gets the total allocated MIPS for a VM over all the PEs.
     *
     * @param vm the vm
     * @return the allocated mips for vm
     */
    public double getTotalAllocatedMipsForVm(Host host, Vm vm) {
        return hostVmSchedulerManagerService.getManager(host).getTotalAllocatedMipsForVm(vm);
    }

    /**
     * Returns maximum available MIPS among all the PEs.
     *
     * @return max mips
     */
    public double getMaxAvailableMips(Host host) {
        return hostVmSchedulerManagerService.getManager(host).getMaxAvailableMips();
    }

    public VmCloudletSchedulerManagerService getVmCloudletSchedulerManager() {
        return vmCloudletSchedulerManagerService;
    }

    public void removeMigratingInVms(Host host, Vm vm) {

        vmDeallocate(host, vm);
        host.getVmsMigratingIn().remove(vm);
        host.getVmList().remove(vm);
        hostVmSchedulerManagerService.getManager(host).getVmsMigratingIn().remove(vm.getUid());
        vm.setInMigration(false);


    }
}
