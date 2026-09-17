import Header from './Header';
import Footer from './Footer';

export default function UserShell({ children }) {
  return (
    <>
      <a className="skip" href="#main">
        본문으로 건너뛰기
      </a>
      <Header />
      <main id="main" className="container">
        {children}
      </main>
      <Footer />
    </>
  );
}
