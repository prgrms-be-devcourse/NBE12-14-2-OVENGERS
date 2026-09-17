import { Outlet } from 'react-router-dom';
import Header from './Header';
import Footer from './Footer';

export default function UserLayout() {
  return (
    <>
      <a className="skip" href="#main">
        본문으로 건너뛰기
      </a>
      <Header />
      <main id="main" className="container">
        <Outlet />
      </main>
      <Footer />
    </>
  );
}
