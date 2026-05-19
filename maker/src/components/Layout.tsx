import { useEffect, useState } from 'react';
import { NavLink, Outlet, useParams } from 'react-router-dom';
import api, { setProjectScope } from '../api';
import MenuBar from './MenuBar';
import type { CSSProperties } from 'react';

const navItems = [
  { label: 'Zones', path: 'zones', description: 'Create and connect rooms on a visual map' },
  { label: 'Items', path: 'items', description: 'Weapons, armor, consumables, crafting materials' },
  { label: 'Recipes', path: 'recipes', description: 'Crafting recipes that combine items' },
  { label: 'NPCs', path: 'npcs', description: 'Non-player characters: vendors, enemies, quest givers' },
  { label: 'Classes', path: 'classes', description: 'Player classes with stats, skills, and magic schools' },
  { label: 'Races', path: 'races', description: 'Player races with stat modifiers' },
  { label: 'Skills', path: 'skills', description: 'Active and passive combat/utility abilities' },
  { label: 'Spells', path: 'spells', description: 'Spells organized by magic school' },
  { label: 'Default Players', path: 'default-players', description: 'Player character sprites per race/class/gender' },
  { label: 'Default SFX', path: 'default-sfx', description: 'Default sound effects for combat, movement, etc.' },
  { label: 'World', path: 'world', description: 'World name, author, intro script metadata' },
];

const styles: Record<string, CSSProperties> = {
  wrapper: {
    display: 'flex',
    flexDirection: 'column',
    height: '100vh',
    overflow: 'hidden',
  },
  container: {
    display: 'flex',
    flex: 1,
    overflow: 'hidden',
  },
  sidebar: {
    width: 220,
    minWidth: 220,
    backgroundColor: '#1a1a2e',
    color: '#e0e0e0',
    display: 'flex',
    flexDirection: 'column',
    padding: 0,
  },
  navSpacer: {
    flex: 1,
  },
  navBottom: {
    display: 'flex',
    flexDirection: 'column',
    gap: 2,
    padding: '0 8px 12px',
    borderTop: '1px solid #2a2a4a',
    marginTop: 8,
    paddingTop: 8,
  },
  projectName: {
    padding: '20px 16px 12px',
    fontSize: 18,
    fontWeight: 700,
    color: '#ffffff',
    borderBottom: '1px solid #2a2a4a',
    marginBottom: 8,
  },
  readOnlyBadge: {
    display: 'inline-block',
    fontSize: 10,
    fontWeight: 600,
    padding: '2px 6px',
    borderRadius: 3,
    backgroundColor: '#3949ab',
    color: '#fff',
    marginLeft: 8,
    verticalAlign: 'middle',
  },
  nav: {
    display: 'flex',
    flexDirection: 'column',
    gap: 2,
    padding: '0 8px',
  },
  link: {
    display: 'block',
    padding: '10px 12px',
    borderRadius: 6,
    color: '#b0b0cc',
    textDecoration: 'none',
    fontSize: 14,
    transition: 'background 0.15s, color 0.15s',
  },
  linkActive: {
    backgroundColor: '#16213e',
    color: '#ffffff',
    fontWeight: 600,
  },
  content: {
    flex: 1,
    backgroundColor: '#f5f5f5',
    overflow: 'auto',
  },
};

function Layout() {
  const { name } = useParams<{ name: string }>();
  const [readOnly, setReadOnly] = useState(false);

  // Set the project scope SYNCHRONOUSLY during render so child editor
  // components see the correct scope on their very first useEffect.
  //
  // Previously this was inside a useEffect, which meant the child's
  // first data-fetching useEffect fired BEFORE the scope was set (React
  // runs child effects before parent effects on mount). The child's
  // api.get('/zones') would resolve to /maker-api/zones (unscoped),
  // which the Express server matches to the SPA catch-all route and
  // returns as text/html — the original-api.ts contract silently cast
  // that to `undefined as Zone[]`, poisoning state and crashing render
  // on the next `.find`/`.filter`/`.map`. api.ts now throws on non-JSON,
  // but the real fix is making the scope available before children
  // fetch — which is right here.
  //
  // setProjectScope is idempotent with the same name, so running it on
  // every render is fine. Cleanup on unmount handled in useEffect below.
  if (name) {
    setProjectScope(name);
  }

  useEffect(() => {
    return () => setProjectScope(null);
  }, []);

  return (
    <div style={styles.wrapper}>
      <MenuBar />
      <div style={styles.container}>
      <div style={styles.sidebar}>
        <div style={styles.projectName}>
          {name}
          {readOnly && <span style={styles.readOnlyBadge}>Read Only</span>}
        </div>
        <nav style={styles.nav}>
          {navItems.map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              title={item.description}
              style={({ isActive }) => ({
                ...styles.link,
                ...(isActive ? styles.linkActive : {}),
              })}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div style={styles.navSpacer} />
        <div style={styles.navBottom}>
          <NavLink
            to="settings"
            title="AI provider API keys and configuration"
            style={({ isActive }) => ({
              ...styles.link,
              ...(isActive ? styles.linkActive : {}),
            })}
          >
            Settings
          </NavLink>
        </div>
      </div>
      <div style={styles.content}>
        <Outlet />
      </div>
      </div>
    </div>
  );
}

export default Layout;
