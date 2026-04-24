import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import AuthGuard from './components/AuthGuard';
import { ErrorBoundary } from './components/ErrorBoundary';
import ProjectList from './pages/ProjectList';
import Dashboard from './pages/Dashboard';
import ZoneEditor from './pages/ZoneEditor';
import ItemEditor from './pages/ItemEditor';
import NpcEditor from './pages/NpcEditor';
import ClassEditor from './pages/ClassEditor';
import RaceEditor from './pages/RaceEditor';
import SkillEditor from './pages/SkillEditor';
import SpellEditor from './pages/SpellEditor';
import RecipeEditor from './pages/RecipeEditor';
import PcSpriteEditor from './pages/PcSpriteEditor';
import DefaultSfxEditor from './pages/DefaultSfxEditor';
import SettingsEditor from './pages/SettingsEditor';

/**
 * Wrap a page in an ErrorBoundary so render-time crashes don't nuke the
 * whole navigation shell — user sees a diagnostic panel with a Retry
 * button inside the Dashboard layout instead.
 */
const wrap = (label: string, node: React.ReactNode) => (
  <ErrorBoundary label={label}>{node}</ErrorBoundary>
)

const ROUTER_BASENAME = import.meta.env.BASE_URL.replace(/\/$/, '') || undefined;

function App() {
  return (
    <AuthGuard>
    <BrowserRouter basename={ROUTER_BASENAME}>
      <Routes>
        <Route path="/" element={<ProjectList />} />
        <Route path="/project/:name" element={<Dashboard />}>
          <Route index element={<Navigate to="zones" replace />} />
          <Route path="zones" element={wrap('Zones', <ZoneEditor />)} />
          <Route path="items" element={wrap('Items', <ItemEditor />)} />
          <Route path="recipes" element={wrap('Recipes', <RecipeEditor />)} />
          <Route path="npcs" element={wrap('NPCs', <NpcEditor />)} />
          <Route path="classes" element={wrap('Classes', <ClassEditor />)} />
          <Route path="races" element={wrap('Races', <RaceEditor />)} />
          <Route path="skills" element={wrap('Skills', <SkillEditor />)} />
          <Route path="spells" element={wrap('Spells', <SpellEditor />)} />
          <Route path="default-players" element={wrap('Default Players', <PcSpriteEditor />)} />
          <Route path="default-sfx" element={wrap('Default SFX', <DefaultSfxEditor />)} />
          <Route path="settings" element={wrap('Settings', <SettingsEditor />)} />
        </Route>
      </Routes>
    </BrowserRouter>
    </AuthGuard>
  );
}

export default App;
