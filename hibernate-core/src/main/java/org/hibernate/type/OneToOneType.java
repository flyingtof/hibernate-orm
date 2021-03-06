/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.engine.jdbc.Size;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.Mapping;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.internal.util.collections.ArrayHelper;
import org.hibernate.persister.entity.EntityPersister;

/**
 * A one-to-one association to an entity
 * @author Gavin King
 */
public class OneToOneType extends EntityType {

	private final ForeignKeyDirection foreignKeyType;
	private final String propertyName;
	private final String entityName;

	/**
	 * @deprecated Use {@link #OneToOneType(TypeFactory.TypeScope, String, ForeignKeyDirection, boolean, String, boolean, boolean, String, String)}
	 *  instead.
	 */
	@Deprecated
	public OneToOneType(
			TypeFactory.TypeScope scope,
			String referencedEntityName,
			ForeignKeyDirection foreignKeyType,
			String uniqueKeyPropertyName,
			boolean lazy,
			boolean unwrapProxy,
			String entityName,
			String propertyName) {
		this( scope, referencedEntityName, foreignKeyType, uniqueKeyPropertyName == null, uniqueKeyPropertyName, lazy, unwrapProxy, entityName, propertyName );
	}

	public OneToOneType(
			TypeFactory.TypeScope scope,
			String referencedEntityName,
			ForeignKeyDirection foreignKeyType,
			boolean referenceToPrimaryKey,
			String uniqueKeyPropertyName,
			boolean lazy,
			boolean unwrapProxy,
			String entityName,
			String propertyName) {
		super( scope, referencedEntityName, referenceToPrimaryKey, uniqueKeyPropertyName, !lazy, unwrapProxy );
		this.foreignKeyType = foreignKeyType;
		this.propertyName = propertyName;
		this.entityName = entityName;
	}

	public String getPropertyName() {
		return propertyName;
	}
	
	public boolean isNull(Object owner, SessionImplementor session) {
		if ( propertyName != null ) {
			final EntityPersister ownerPersister = session.getFactory().getEntityPersister( entityName );
			final Serializable id = session.getContextEntityIdentifier( owner );
			final EntityKey entityKey = session.generateEntityKey( id, ownerPersister );
			return session.getPersistenceContext().isPropertyNull( entityKey, getPropertyName() );
		}
		else {
			return false;
		}
	}

	public int getColumnSpan(Mapping session) throws MappingException {
		return 0;
	}

	public int[] sqlTypes(Mapping session) throws MappingException {
		return ArrayHelper.EMPTY_INT_ARRAY;
	}

	private static final Size[] SIZES = new Size[0];

	@Override
	public Size[] dictatedSizes(Mapping mapping) throws MappingException {
		return SIZES;
	}

	@Override
	public Size[] defaultSizes(Mapping mapping) throws MappingException {
		return SIZES;
	}

	public boolean[] toColumnNullness(Object value, Mapping mapping) {
		return ArrayHelper.EMPTY_BOOLEAN_ARRAY;
	}

	public void nullSafeSet(PreparedStatement st, Object value, int index, boolean[] settable, SessionImplementor session) {
		//nothing to do
	}

	public void nullSafeSet(PreparedStatement st, Object value, int index, SessionImplementor session) {
		//nothing to do
	}

	public boolean isOneToOne() {
		return true;
	}

	public boolean isDirty(Object old, Object current, SessionImplementor session) {
		return false;
	}

	public boolean isDirty(Object old, Object current, boolean[] checkable, SessionImplementor session) {
		return false;
	}

	public boolean isModified(Object old, Object current, boolean[] checkable, SessionImplementor session) {
		return false;
	}

	public ForeignKeyDirection getForeignKeyDirection() {
		return foreignKeyType;
	}

	public Object hydrate(
		ResultSet rs,
		String[] names,
		SessionImplementor session,
		Object owner)
	throws HibernateException, SQLException {

		return session.getContextEntityIdentifier(owner);
	}

	protected boolean isNullable() {
		return foreignKeyType==ForeignKeyDirection.TO_PARENT;
	}

	public boolean useLHSPrimaryKey() {
		return true;
	}

	public Serializable disassemble(Object value, SessionImplementor session, Object owner)
	throws HibernateException {
		return null;
	}

	public Object assemble(Serializable oid, SessionImplementor session, Object owner)
	throws HibernateException {
		//this should be a call to resolve(), not resolveIdentifier(), 
		//'cos it might be a property-ref, and we did not cache the
		//referenced value
		return resolve( session.getContextEntityIdentifier(owner), session, owner );
	}
	
	/**
	 * We don't need to dirty check one-to-one because of how 
	 * assemble/disassemble is implemented and because a one-to-one 
	 * association is never dirty
	 */
	public boolean isAlwaysDirtyChecked() {
		//TODO: this is kinda inconsistent with CollectionType
		return false; 
	}
}
