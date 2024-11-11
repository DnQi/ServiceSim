/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.infrastructureProvider.policies.provisioners;

import org.infrastructureProvider.entities.Host;
import org.infrastructureProvider.entities.Vm;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * RamProvisionerSimple is an extension of RamProvisioner which uses a best-effort policy to
 * allocate memory to a VM.
 *
 * @author Rodrigo N. Calheiros
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 1.0
 */
public class VmResourceProvisionerSimple<TResource> implements VmResourceProvisioner<TResource> {
    public static VmResourceProvisionerSimple<Integer> RamProvisioner = new VmResourceProvisionerSimple<Integer>(
            Host::getTotalRam,
            Host::getAvailableRam,
            (h, r) -> h.setAvailableRam(h.getAvailableRam() - r),
            (h, r) -> h.setAvailableRam(h.getAvailableRam() + r),
            Host::setAvailableRam,
            Vm::getRam,
            Vm::setRam,
            Comparator.naturalOrder(),
            0
    );
	public static VmResourceProvisionerSimple<Long> BwProvisioner = new VmResourceProvisionerSimple<Long>(
			Host::getTotalBw,
            Host::getAvailableBw,
            (h, b) -> h.setAvailableBw(h.getAvailableBw() - b),
            (h, b) -> h.setAvailableBw(h.getAvailableBw() + b),
            Host::setAvailableBw,
            Vm::getBw,
            Vm::setBw,
            Comparator.naturalOrder(),
            0L
    );
    public static VmResourceProvisionerSimple<Double> MipsProvisioner = new VmResourceProvisionerSimple<Double>(
            Host::getTotalMips,
            Host::getAvailableMips,
            (h, m) -> h.setAvailableMips(h.getAvailableMips() - m),
            (h, m) -> h.setAvailableMips(h.getAvailableMips() + m),
            Host::setAvailableMips,
            Vm::getMips,
            Vm::setMips,
            Comparator.naturalOrder(),
            0.0

    );

    private final Map<String, TResource> resourceMap = new HashMap<>();
    private final Function<Host, TResource> hostTotalResourceGetter;
    private final Function<Host, TResource> hostAvailableResourceGetter;
    private final BiConsumer<Host, TResource> hostSubstractAvailableResource;
    private final BiConsumer<Host, TResource> hostAddAvailableResource;
    private final BiConsumer<Host, TResource> hostSetAvailableResource;
    private final Function<Vm, TResource> vmResourceGetter;
    private final BiConsumer<Vm, TResource> vmResourceSetter;
    private final Comparator<TResource> comparator;
    private final TResource _default;

    public VmResourceProvisionerSimple(Function<Host, TResource> hostTotalResourceGetter, Function<Host, TResource> hostAvailableResourceGetter, BiConsumer<Host, TResource> hostSubstractAvailableResource, BiConsumer<Host, TResource> hostAddAvailableResource, BiConsumer<Host, TResource> hostSetAvailableResource, Function<Vm, TResource> vmResourceGetter, BiConsumer<Vm, TResource> vmResourceSetter, Comparator<TResource> comparator, TResource aDefault) {
        this.hostTotalResourceGetter = hostTotalResourceGetter;
        this.hostAvailableResourceGetter = hostAvailableResourceGetter;
        this.hostSubstractAvailableResource = hostSubstractAvailableResource;
        this.hostAddAvailableResource = hostAddAvailableResource;
        this.hostSetAvailableResource = hostSetAvailableResource;
        this.vmResourceGetter = vmResourceGetter;
        this.vmResourceSetter = vmResourceSetter;
        this.comparator = comparator;

        _default = aDefault;
    }

//	public <TResource, T> VmResourceProvisionerSimple(Function<Host, TResource> getTotalRam, Function<Host,TResource> getAvailableRam, BiConsumer<Host,TResource> hostTResourceBiConsumer, BiConsumer<Host,TResource> hostTResourceBiConsumer1, BiConsumer<Host,TResource> setAvailableRam, Function<Vm,TResource> getRam1, BiConsumer<Vm,TResource> setRam, Comparator<T> tComparator, Integer integer) {
//	}

    private TResource Min(TResource a, TResource b) {
        if (comparator.compare(a, b) < 0) {
            return a;
        }
        return b;
    }

    @Override
    public boolean allocateForVm(Host host, Vm vm, TResource res) {
        var maxRam = vmResourceGetter.apply(vm);
        res = Min(res, maxRam);

        deallocateForVm(host, vm);

        if (comparator.compare(hostAvailableResourceGetter.apply(host), res) >= 0) {
            hostSubstractAvailableResource.accept(host, res);
            //host.setAvailableRam(host.getAvailableRam() - res);
            resourceMap.put(vm.getUid(), res);
            vmResourceSetter.accept(vm, getAllocatedForVm(host, vm));
            return true;
        }
        vmResourceSetter.accept(vm, getAllocatedForVm(host, vm));
        return false;
    }

    @Override
    public TResource getAllocatedForVm(Host host, Vm vm) {
        return resourceMap.getOrDefault(vm.getUid(), _default);
    }

    @Override
    public void deallocateForVm(Host host, Vm vm) {
        if (resourceMap.containsKey(vm.getUid())) {
            var amountFreed = resourceMap.remove(vm.getUid());
            hostAddAvailableResource.accept(host, amountFreed);
            vmResourceSetter.accept(vm, _default);
        }
    }

    @Override
    public void deallocateForAllVms(Host host) {
        hostSetAvailableResource.accept(host, hostTotalResourceGetter.apply(host));
        resourceMap.clear();
    }

    @Override
    public boolean isSuitableForVm(Host host, Vm vm, TResource res) {
        var allocatedRes = getAllocatedForVm(host, vm);
        boolean result = allocateForVm(host, vm, res);
        deallocateForVm(host, vm);

        if (comparator.compare(allocatedRes, _default) > 0) {
            allocateForVm(host, vm, allocatedRes);
        }

        return result;
    }
}

