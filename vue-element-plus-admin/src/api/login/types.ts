export interface UserLoginType {
  username: string
  password: string
}

export interface UserDepartmentType {
  id: string
  departmentName?: string
}

export interface UserType {
  id?: string
  username: string
  password?: string
  account?: string
  email?: string
  status?: number
  role?: string
  roleId?: string
  roleCodes?: string[]
  permissions?: string[]
  tokenKey?: string
  token?: string
  expiresAt?: string
  department?: UserDepartmentType
}

export interface LoginResponse extends UserType {
  tokenKey: string
  token: string
}

export interface CurrentUserResponse extends Omit<UserType, 'password' | 'role' | 'roleId'> {}
