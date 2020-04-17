/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.event.spi;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.hibernate.HibernateException;
import org.hibernate.internal.CoreLogging;

import org.jboss.logging.Logger;

/**
 * Enumeration of the recognized types of events, including meta-information about each.
 *
 * @author Steve Ebersole
 */
public final class EventType<T> {
	private static final Logger LOG = CoreLogging.logger( EventType.class );
	private static AtomicInteger typeCounter = new AtomicInteger( 0 );

	public static final EventType<LoadEventListener> LOAD = create( "load", LoadEventListener.class );
	public static final EventType<ResolveNaturalIdEventListener> RESOLVE_NATURAL_ID = create( "resolve-natural-id", ResolveNaturalIdEventListener.class );

	public static final EventType<InitializeCollectionEventListener> INIT_COLLECTION = create( "load-collection", InitializeCollectionEventListener.class );

	public static final EventType<SaveOrUpdateEventListener> SAVE_UPDATE = create( "save-update", SaveOrUpdateEventListener.class );
	public static final EventType<SaveOrUpdateEventListener> UPDATE = create( "update", SaveOrUpdateEventListener.class );
	public static final EventType<SaveOrUpdateEventListener> SAVE = create( "save", SaveOrUpdateEventListener.class );
	public static final EventType<PersistEventListener> PERSIST = create( "create", PersistEventListener.class );
	public static final EventType<PersistEventListener> PERSIST_ONFLUSH = create( "create-onflush", PersistEventListener.class );

	public static final EventType<MergeEventListener> MERGE = create( "merge", MergeEventListener.class );

	public static final EventType<DeleteEventListener> DELETE = create( "delete", DeleteEventListener.class );

	public static final EventType<ReplicateEventListener> REPLICATE = create( "replicate", ReplicateEventListener.class );

	public static final EventType<FlushEventListener> FLUSH = create( "flush", FlushEventListener.class );
	public static final EventType<AutoFlushEventListener> AUTO_FLUSH = create( "auto-flush", AutoFlushEventListener.class );
	public static final EventType<DirtyCheckEventListener> DIRTY_CHECK = create( "dirty-check", DirtyCheckEventListener.class );
	public static final EventType<FlushEntityEventListener> FLUSH_ENTITY = create( "flush-entity", FlushEntityEventListener.class );

	public static final EventType<ClearEventListener> CLEAR = create( "clear", ClearEventListener.class );
	public static final EventType<EvictEventListener> EVICT = create( "evict", EvictEventListener.class );

	public static final EventType<LockEventListener> LOCK = create( "lock", LockEventListener.class );

	public static final EventType<RefreshEventListener> REFRESH = create( "refresh", RefreshEventListener.class );

	public static final EventType<PreLoadEventListener> PRE_LOAD = create( "pre-load", PreLoadEventListener.class );
	public static final EventType<PreDeleteEventListener> PRE_DELETE = create( "pre-delete", PreDeleteEventListener.class );
	public static final EventType<PreUpdateEventListener> PRE_UPDATE = create( "pre-update", PreUpdateEventListener.class );
	public static final EventType<PreInsertEventListener> PRE_INSERT = create( "pre-insert", PreInsertEventListener.class );

	public static final EventType<PostLoadEventListener> POST_LOAD = create( "post-load", PostLoadEventListener.class );
	public static final EventType<PostDeleteEventListener> POST_DELETE = create( "post-delete", PostDeleteEventListener.class );
	public static final EventType<PostUpdateEventListener> POST_UPDATE = create( "post-update", PostUpdateEventListener.class );
	public static final EventType<PostInsertEventListener> POST_INSERT = create( "post-insert", PostInsertEventListener.class );

	public static final EventType<PostDeleteEventListener> POST_COMMIT_DELETE = create( "post-commit-delete", PostDeleteEventListener.class );
	public static final EventType<PostUpdateEventListener> POST_COMMIT_UPDATE = create( "post-commit-update", PostUpdateEventListener.class );
	public static final EventType<PostInsertEventListener> POST_COMMIT_INSERT = create( "post-commit-insert", PostInsertEventListener.class );

	public static final EventType<PreCollectionRecreateEventListener> PRE_COLLECTION_RECREATE = create( "pre-collection-recreate", PreCollectionRecreateEventListener.class );
	public static final EventType<PreCollectionRemoveEventListener> PRE_COLLECTION_REMOVE = create( "pre-collection-remove", PreCollectionRemoveEventListener.class );
	public static final EventType<PreCollectionUpdateEventListener> PRE_COLLECTION_UPDATE = create( "pre-collection-update", PreCollectionUpdateEventListener.class );

	public static final EventType<PostCollectionRecreateEventListener> POST_COLLECTION_RECREATE = create( "post-collection-recreate", PostCollectionRecreateEventListener.class );
	public static final EventType<PostCollectionRemoveEventListener> POST_COLLECTION_REMOVE = create( "post-collection-remove", PostCollectionRemoveEventListener.class );
	public static final EventType<PostCollectionUpdateEventListener> POST_COLLECTION_UPDATE = create( "post-collection-update", PostCollectionUpdateEventListener.class );

	/**
	 * Add a new event type.
	 *
	 * @param name - name of the custom event
	 * @param listenerClass - the base listener class or interface associated with the entity type
	 * @param <T> - listenerClass
	 * @return the custom {@link EventType}
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static synchronized <T> EventType<T> addCustomEventType(String name, Class<T> listenerClass) {
		if ( name == null || listenerClass == null ) {
			throw new HibernateException( "Custom EventType name and associated class must be non-null." );
		}

		final EventType eventType = EVENT_TYPE_BY_NAME_MAP.computeIfAbsent(
				name,
				( e -> {
					final EventType eventTypeNew = EventType.create( name, listenerClass );
					LOG.debugf(
							"Added custom EventType: [%s], ordinal=[%d], listener=[%s].",
							name,
							eventTypeNew.ordinal,
							listenerClass.toString()
					);
					return eventTypeNew;
				} )
		);
		// There's no way to know if there was a pre-existing EventType with
		// the same name and listener, so ignore that case.
		// Just check that listener is the same as listenerClass
		if ( !listenerClass.equals( eventType.baseListenerInterface ) ) {
				throw new HibernateException(
						"Could not add EventType [" + name + "] with listener Class ["
								+ "]. An EventType with that name already exists with listener ["
								+ listenerClass.getName()
								+ "]."
				);
		}
		return eventType;
	}

	private static <T> EventType<T> create(String name, Class<T> listenerClass) {
		return new EventType<>( name, listenerClass );
	}

	/**
	 * Maintain a map of {@link EventType} instances keyed by name for lookup by name as well as {@link #values()}
	 * resolution.
	 */
	private static final Map<String,EventType> EVENT_TYPE_BY_NAME_MAP = AccessController.doPrivileged(
			new PrivilegedAction<Map<String, EventType>>() {
				@Override
				public Map<String, EventType> run() {
					final Map<String, EventType> typeByNameMap = new ConcurrentHashMap<>();
					for ( Field field : EventType.class.getDeclaredFields() ) {
						if ( EventType.class.isAssignableFrom( field.getType() ) ) {
							try {
								final EventType typeField = (EventType) field.get( null );
								typeByNameMap.put( typeField.eventName(), typeField );
							}
							catch (Exception t) {
								throw new HibernateException( "Unable to initialize EventType map", t );
							}
						}
					}
					return typeByNameMap;
				}
			}
	);

	/**
	 * Find an {@link EventType} by its name
	 *
	 * @param eventName The name
	 *
	 * @return The {@link EventType} instance.
	 *
	 * @throws HibernateException If eventName is null, or if eventName does not correlate to any known event type.
	 */
	public static EventType resolveEventTypeByName(final String eventName) {
		if ( eventName == null ) {
			throw new HibernateException( "event name to resolve cannot be null" );
		}
		final EventType eventType = EVENT_TYPE_BY_NAME_MAP.get( eventName );
		if ( eventType == null ) {
			throw new HibernateException( "Unable to locate proper event type for event name [" + eventName + "]" );
		}
		return eventType;
	}

	/**
	 * Get a collection of all {@link EventType} instances.
	 *
	 * @return All {@link EventType} instances
	 */
	public static Collection<EventType> values() {
		return EVENT_TYPE_BY_NAME_MAP.values();
	}

	private final String eventName;
	private final Class<T> baseListenerInterface;
	private final int ordinal;

	private EventType(String eventName, Class<T> baseListenerInterface) {
		this.eventName = eventName;
		this.baseListenerInterface = baseListenerInterface;
		this.ordinal = typeCounter.getAndIncrement();
	}

	public String eventName() {
		return eventName;
	}

	public Class baseListenerInterface() {
		return baseListenerInterface;
	}

	@Override
	public String toString() {
		return eventName();
	}

	/**
	 * EventType is effectively an enumeration. Since there is a known, limited number of possible types, we expose an
	 * ordinal for each in order to be able to efficiently do associations elsewhere in the codebase (array vs. Map)
	 *
	 * For the total number of types, see {@link #values()}
	 *
	 * @return An unique ordinal for this {@link EventType}, starting at 0 and up to the number of distinct events
	 */
	public int ordinal() {
		return ordinal;
	}

}
