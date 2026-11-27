import { AdminSessionApi, AuthApi, Configuration, CourseApi, EnrollmentApi } from '@eduhub/api-client';
import { tokenStore } from './tokenStore';

// 생성된 클라이언트의 인스턴스는 여기 한 곳에서만 만든다 (Axios를 직접 생성하지 않는다)
// basePath '': 같은 출처(3000)로 요청하고, vite 개발 서버 프록시가 /api 를 백엔드(8080)로 넘긴다
// accessToken: 요청마다 저장된 토큰을 읽어 Authorization 헤더로 붙인다
const configuration = new Configuration({ basePath: '', accessToken: () => tokenStore.getToken() ?? '' });
export const courseApi = new CourseApi(configuration);
export const enrollmentApi = new EnrollmentApi(configuration);
export const authApi = new AuthApi(configuration);
export const adminSessionApi = new AdminSessionApi(configuration);
