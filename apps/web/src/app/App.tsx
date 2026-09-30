import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { Layout } from './Layout';
import { Welcome } from '../routes/Welcome';
import { Search } from '../routes/Search';
import { Results } from '../routes/Results';
import { Journey } from '../routes/Journey';
import { Booking } from '../routes/Booking';
import { Operators, OperatorDetail } from '../routes/Operators';
import { Stations, StationDetail } from '../routes/Stations';
import { Saved } from '../routes/Saved';
import { Offline } from '../routes/Offline';
import { Wallet } from '../routes/Wallet';
import { Settings } from '../routes/Settings';
import { Coverage } from '../routes/Coverage';
import { Licences } from '../routes/Licences';
import { NotFound } from '../routes/NotFound';
import { hasOnboarded } from '../features/onboarding';

/**
 * Sends a first-time reader to the welcome page, but only from the root.
 *
 * A deep link is never intercepted: someone who followed a link to a journey, an
 * operator or a station gets that page, which is the whole point of the link. The
 * welcome page is an introduction, not a gate.
 */
function Entry() {
  const location = useLocation();
  return <Navigate to={hasOnboarded() ? '/search' : '/welcome'} replace state={{ from: location.pathname }} />;
}

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Entry />} />
        <Route path="/welcome" element={<Welcome />} />
        <Route path="/search" element={<Search />} />
        <Route path="/results" element={<Results />} />
        <Route path="/journey/:id" element={<Journey />} />
        <Route path="/journey/:id/booking" element={<Booking />} />
        <Route path="/operators" element={<Operators />} />
        <Route path="/operators/:id" element={<OperatorDetail />} />
        <Route path="/stations" element={<Stations />} />
        <Route path="/stations/:id" element={<StationDetail />} />
        <Route path="/saved" element={<Saved />} />
        <Route path="/offline" element={<Offline />} />
        <Route path="/wallet" element={<Wallet />} />
        <Route path="/settings" element={<Settings />} />
        <Route path="/coverage" element={<Coverage />} />
        <Route path="/licences" element={<Licences />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
