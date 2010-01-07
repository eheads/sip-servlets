/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.mobicents.servlet.sip.core.timers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.jboss.web.tomcat.service.session.ClusteredSipManager;
import org.jboss.web.tomcat.service.session.distributedcache.spi.OutgoingDistributableSessionData;
import org.mobicents.servlet.sip.core.session.DistributableSipManager;
import org.mobicents.servlet.sip.startup.SipApplicationSessionTimerService;
import org.mobicents.timers.FaultTolerantScheduler;
import org.mobicents.timers.TimerTask;
import org.mobicents.timers.TimerTaskFactory;

/**
 * Fault Tolerant implementation of SipApplicationSessionTimerService allowing to 
 * shedule Sip application session Timers that can be failed over
 * 
 * @author jean.deruelle@gmail.com
 *
 */
public class FaultTolerantSasTimerService implements SipApplicationSessionTimerService {

	private static final Logger logger = Logger.getLogger(FaultTolerantSasTimerService.class
			.getName());
	public static final String NAME = "MSS_FT_SAS_Timers";
	
	private FaultTolerantScheduler scheduledExecutor;
	private ClusteredSipManager<? extends OutgoingDistributableSessionData> sipManager;
	private int corePoolSize;
	
	public FaultTolerantSasTimerService(DistributableSipManager sipManager, int corePoolSize) {
		this.corePoolSize = corePoolSize;
		this.sipManager = (ClusteredSipManager<? extends OutgoingDistributableSessionData>)sipManager;	
	}
	
	public ScheduledFuture<?> schedule(SipApplicationSessionTimerTask expirationTimerTask, 
            long delay, 
            TimeUnit unit) {			
		if(expirationTimerTask instanceof FaultTolerantSasTimerTask) {
			FaultTolerantSasTimerTask faultTolerantSasTimerTask = (FaultTolerantSasTimerTask)expirationTimerTask;
			faultTolerantSasTimerTask.getData().setStartTime(System.currentTimeMillis() + delay);
			if(logger.isDebugEnabled()) {
				logger.debug("Scheduling sip application session "+ expirationTimerTask.getSipApplicationSession().getKey() +" to expire in " + (delay / (double) 1000 / (double) 60) + " minutes");
			}
			getScheduler().schedule(faultTolerantSasTimerTask);
			return faultTolerantSasTimerTask.getScheduledFuture();
		} 				
		throw new IllegalArgumentException("the task to schedule is not an instance of FaultTolerantSasTimerTask");
	}
	
	public boolean remove(SipApplicationSessionTimerTask expirationTimerTask) {
		if(expirationTimerTask instanceof FaultTolerantSasTimerTask) {
			TimerTask cancelledTask = getScheduler().cancel(((FaultTolerantSasTimerTask)expirationTimerTask).getData().getTaskID());
			return true;
		} 				
		throw new IllegalArgumentException("the task to remove is not an instance of FaultTolerantSasTimerTask");
	}
	
	public void purge() {
//		super.purge();
		// method not exposed by Mobicents FaultTolerantScheduler
	}
	
	public List<Runnable> shutdownNow() {
//		return super.shutdownNow();
		// method not exposed by Mobicents FaultTolerantScheduler
		return new ArrayList<Runnable>();
	}
	
	public FaultTolerantScheduler getScheduler() {
		if(scheduledExecutor == null) {
			TimerTaskFactory timerTaskFactory = new SipApplicationSessionTaskFactory(this.sipManager);
			scheduledExecutor = new FaultTolerantScheduler(NAME, corePoolSize, this.sipManager.getMobicentsCluster(), (byte) 0, null, timerTaskFactory);
		}
		return scheduledExecutor;
	}

}
